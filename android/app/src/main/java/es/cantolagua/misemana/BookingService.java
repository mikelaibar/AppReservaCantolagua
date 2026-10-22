package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BookingService extends Service {
  private static final AtomicBoolean RUNNING = new AtomicBoolean();
  // In-process dependency seam for signed instrumentation; never exposed via intents.
  static java.util.function.Supplier<ClubClient> clients = ClubClient::new;
  private volatile boolean stopped;

  public IBinder onBind(Intent i) {
    return null;
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    Store started = new Store(this);
    started.woke();
    started.stage("Servicio de reservas iniciado");
    Notification n =
        Notices.notification(
            this,
            "Preparando las reservas",
            "Conectando con Cantolagua. Puedes cerrar la aplicación");
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    else startForeground(1, n);
    if (RUNNING.compareAndSet(false, true)) new Thread(this::run, "cantolagua-booking").start();
    return START_NOT_STICKY;
  }

  private boolean active(Store s, String revision) {
    return !stopped && s.enabled() && revision.equals(s.revision());
  }

  private void run() {
    Store store = new Store(this);
    PowerManager.WakeLock wake =
        getSystemService(PowerManager.class)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MiCantolagua:booking");
    wake.acquire(180000);
    String account = store.account(), revision = store.revision();
    long start = SystemClock.elapsedRealtime();
    List<Plan.Job> batch = new ArrayList<>();
    long clockStart = System.currentTimeMillis();
    try {
      if (!active(store, revision)) return;
      List<Plan.Job> pending = store.jobs();
      if (pending.isEmpty()) return;
      Instant release = pending.get(0).release;
      if (release.isAfter(Instant.now().plusSeconds(70))) return;
      for (Plan.Job j : pending) if (j.release.equals(release)) batch.add(j);
      ClubClient client = clients.get();
      store.stage("Iniciando sesión en Cantolagua");
      client.login(store.user(), store.password());
      store.stage("Cuenta autenticada; esperando o consultando la apertura");
      long wall = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
      while (Instant.now().isBefore(release)) {
        if (!active(store, revision) || SystemClock.elapsedRealtime() - start > 150000) return;
        if (Math.abs(
                (System.currentTimeMillis() - wall) - (SystemClock.elapsedRealtime() - elapsed))
            > 3000)
          throw new ClubClient.Problem("El reloj del teléfono ha cambiado durante la preparación");
        Thread.sleep(
            Math.min(250, Math.max(1, release.toEpochMilli() - System.currentTimeMillis())));
      }
      // Give a late wake-up its own bounded query window instead of dropping the job.
      Instant queryDeadline = Instant.now().plusSeconds(90);
      List<Plan.Job> remaining = new ArrayList<>(batch);
      while (active(store, revision)
          && !remaining.isEmpty()
          && SystemClock.elapsedRealtime() - start < 150000
          && Instant.now().isBefore(queryDeadline)) {
        List<ClubClient.Session> catalogue;
        try {
          store.stage("Consultando disponibilidad con la cuenta autenticada");
          catalogue = client.catalogue();
          store.catalogue(catalogue);
        } catch (Exception e) {
          Thread.sleep(2000);
          continue;
        }
        Iterator<Plan.Job> iterator = remaining.iterator();
        while (iterator.hasNext()) {
          Plan.Job job = iterator.next();
          if (!job.date.atTime(LocalTime.parse(job.slot.time)).atZone(Plan.ZONE)
              .toInstant().isAfter(Instant.now())) {
            if (store.claim(account, job, null))
              store.finish(account, job, "No reservada", "La sesión ya ha comenzado");
            iterator.remove();
            continue;
          }
          if (!active(store, revision) || !store.desired(job) || store.attempted(job.key())) {
            iterator.remove();
            continue;
          }
          ClubClient.Session session;
          try {
            session = ClubClient.match(catalogue, job);
          } catch (ClubClient.Problem e) {
            if (store.claim(account, job, null))
              store.finish(account, job, "No reservada", e.getMessage());
            iterator.remove();
            continue;
          }
          if (session == null) continue;
          if (!session.open) {
            if (session.booked
                || session.waitlisted
                || session.status.equals("Completa")
                || session.status.equals("Importe no reconocido")
                || session.paid) {
              if (store.claim(account, job, null)) {
                store.finish(
                    account,
                    job,
                    session.status,
                    session.detail);
                Notices.result(this, session.status, job.label());
                store.stage("Resultado: " + session.status);
              }
              iterator.remove();
            }
            continue;
          }
          synchronized (Store.LOCK) {
            if (!active(store, revision)
                || !store.desired(job)
                || !Instant.now().isBefore(queryDeadline)
                || SystemClock.elapsedRealtime() - start > 150000
                || Math.abs(
                        (System.currentTimeMillis() - clockStart)
                            - (SystemClock.elapsedRealtime() - start))
                    > 3000) continue;
            if (!store.claim(account, job, session.id)) {
              iterator.remove();
              continue;
            }
          }
          iterator.remove();
          try {
            store.stage("Enviando y comprobando la reserva");
            ClubClient.Outcome outcome = client.reserveVerified(session, job);
            store.finish(
                account,
                job,
                outcome.confirmed ? "Reservada" : "Comprobar en la web",
                outcome.detail);
            Notices.result(
                this, outcome.confirmed ? "¡Plaza reservada!" : "Comprueba tu reserva", job.label());
            store.stage(outcome.confirmed ? "Reserva confirmada" : "Resultado incierto: revisar la web");
          } catch (Exception e) {
            store.stage("Resultado incierto: revisar la web");
            store.finish(
                account,
                job,
                "Comprobar en la web",
                "La conexión o la respuesta falló tras iniciar la solicitud. No se repetirá"
                    + " automáticamente");
            Notices.result(
                this,
                "Comprueba tu reserva",
                job.label() + ". Abre Cantolagua para verificar si tienes plaza");
          }
        }
        if (!remaining.isEmpty()) Thread.sleep(2000);
      }
      for (Plan.Job job : remaining)
        if (active(store, revision) && store.desired(job) && store.claim(account, job, null))
          store.finish(
              account,
              job,
              "No reservada",
              "No se encontró una plaza disponible durante la consulta. Comprueba el horario y"
                  + " los días de antelación");

    } catch (Exception e) {
      store.stage("No se pudo preparar la conexión o iniciar sesión");
      for (Plan.Job job : batch)
        if (!store.attempted(job.key()) && store.claim(account, job, null))
          store.finish(
              account,
              job,
              "No reservada",
              "No se pudo preparar la conexión. Revisa tus credenciales y la conexión a Internet");
      Notices.result(
          this,
          "No se pudo completar la reserva",
          "Revisa el resultado en la aplicación y comprueba tu acceso a Cantolagua");
    } finally {
      if (wake.isHeld()) wake.release();
      RUNNING.set(false);
      try {
        Scheduler.refresh(this);
      } catch (RuntimeException ignored) {
      }
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
    }
  }

  public void onTimeout(int startId, int type) {
    stopped = true;
    stopSelf();
  }

  public void onDestroy() {
    stopped = true;
    super.onDestroy();
  }
}
