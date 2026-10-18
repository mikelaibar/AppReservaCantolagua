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
  private volatile boolean stopped;

  public IBinder onBind(Intent i) {
    return null;
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    new Store(this).woke();
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
      ClubClient client = new ClubClient();
      client.login(store.user(), store.password());
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
      List<Plan.Job> remaining = new ArrayList<>(batch);
      while (active(store, revision)
          && !remaining.isEmpty()
          && SystemClock.elapsedRealtime() - start < 150000
          && Instant.now().isBefore(release.plusSeconds(90))) {
        List<ClubClient.Session> catalogue;
        try {
          catalogue = client.catalogue();
          store.catalogue(catalogue);
        } catch (Exception e) {
          Thread.sleep(2000);
          continue;
        }
        Iterator<Plan.Job> iterator = remaining.iterator();
        while (iterator.hasNext()) {
          Plan.Job job = iterator.next();
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
                || session.paid) {
              if (store.claim(account, job, null)) {
                store.finish(
                    account,
                    job,
                    session.status,
                    "Estado consultado en Cantolagua. No se ha enviado una reserva");
                Notices.result(this, session.status, job.label());
              }
              iterator.remove();
            }
            continue;
          }
          synchronized (Store.LOCK) {
            if (!active(store, revision)
                || !store.desired(job)
                || !Instant.now().isBefore(release.plusSeconds(90))
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
            boolean confirmed = client.reserve(session);
            if (!confirmed) {
              try {
                ClubClient.Session check = ClubClient.match(client.catalogue(), job);
                confirmed = check != null && check.booked;
              } catch (Exception ignored) {
              }
            }
            store.finish(
                account,
                job,
                confirmed ? "Reservada" : "Comprobar en la web",
                confirmed
                    ? "Cantolagua ha confirmado tu plaza"
                    : "La respuesta no confirma la plaza. Revisa la web antes de repetir");
            Notices.result(
                this, confirmed ? "¡Plaza reservada!" : "Comprueba tu reserva", job.label());
          } catch (Exception e) {
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
              "No se encontró una plaza disponible en el margen de apertura. Comprueba el horario y"
                  + " los días de antelación");

    } catch (Exception e) {
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
