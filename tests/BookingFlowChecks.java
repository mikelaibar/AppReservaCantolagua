package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Isolated emulator only: real service/alarm/storage/notifications, simulated club HTTP. */
public final class BookingFlowChecks extends Instrumentation {
  int checks;
  final StringBuilder report = new StringBuilder();

  void check(boolean ok, String label) {
    if (!ok) throw new AssertionError(label);
    checks++;
    report.append("PASS ").append(label).append('\n');
  }

  public void onCreate(Bundle args) { super.onCreate(args); start(); }

  static final class Server implements ClubClient.Transport {
    final Plan.Job job;
    final String mode;
    boolean booked, loggedIn;
    int sends, logins, postReads;

    Server(Plan.Job job, String mode) { this.job = job; this.mode = mode; }

    public String send(String url, Map<String, String> fields, boolean mutation) throws Exception {
      if (url.contains("login.aspx")) {
        if (fields == null) return "<form id='frmLogin'></form>";
        if (!"flow-test".equals(fields.get("mail"))
            || !"synthetic-password".equals(fields.get("clave")))
          throw new AssertionError("Wrong synthetic login");
        loggedIn = true; logins++;
        return "<body>Inicio</body>";
      }
      if (!loggedIn) throw new AssertionError("Request before login");
      if (mode.equals("login-failure")) return "<body><form id='frmLogin'></form></body>";
      if (mutation) {
        Map<String, String> q = ClubClient.query(new java.net.URI(url));
        if (!"d".equals(q.get("a")) || !"641".equals(q.get("R"))
            || !"99".equals(q.get("IdInscrito"))) throw new AssertionError("Wrong booking action");
        sends++;
        booked = !mode.equals("uncertain");
        if (!mode.equals("success")) throw new IOException("Simulated lost response");
        return "{\"code\":\"OK\",\"result\":{\"asistenciasCreadas\":[{\"clase\":641}]}}";
      }
      if (sends > 0) postReads++;
      String date = Plan.DAYS[job.date.getDayOfWeek().getValue() - 1].toLowerCase(Locale.ROOT)
          + " " + job.date.getDayOfMonth() + "/" + job.date.getMonthValue();
      return "<body class='identificado'><select id='IdInscrito'><option selected value='99'>"
          + "Prueba</option></select><div id='cuadricula'><div class='Clases_Clase Actividad7 "
          + "Clase__Clicable " + (booked ? "Clase__Inscrito" : "")
          + "' id='Clase_641' data-fecha='" + date
          + "' data-importe='Incluida en Abono Actividades'>"
          + "<span class='hinicio_clase'>09:00</span><div class='Clase_Actividad'>"
          + "<a href='r.aspx?a=" + (booked ? "borrardeclase" : "d")
          + "&amp;R=641'>Pilates</a></div></div></div></body>";
    }
  }

  boolean running() throws Exception {
    java.lang.reflect.Field field = BookingService.class.getDeclaredField("RUNNING");
    field.setAccessible(true);
    return ((AtomicBoolean) field.get(null)).get();
  }

  void awaitResult(Store store, String state) throws Exception {
    long until = SystemClock.elapsedRealtime() + 90000;
    while (SystemClock.elapsedRealtime() < until) {
      List<String[]> rows = store.history();
      if (!running() && rows.size() == 1 && rows.get(0)[2].equals(state)) return;
      Thread.sleep(250);
    }
    throw new AssertionError("Service did not finish with " + state + "; rows="
        + store.history().size() + "; status=" + store.status());
  }

  Server prepare(Store store, String mode) throws Exception {
    store.erase();
    store.credentials("flow-test", "synthetic-password");
    LocalDate date = LocalDate.now(Plan.ZONE).plusDays(3);
    Plan.Slot slot = new Plan.Slot(date.getDayOfWeek().getValue() - 1, "7", "Pilates", "09:00");
    Plan.Job job = new Plan.Job(slot, date, 4);
    store.slots(Plan.monday(date), Collections.singletonList(slot), false);
    ClubClient.Session fixture = new ClubClient.Session();
    fixture.date = date; fixture.time = "09:00"; fixture.activity = "7"; fixture.name = "Pilates";
    store.catalogue(Collections.singletonList(fixture));
    Server server = new Server(job, mode);
    BookingService.clients = () -> new ClubClient(server);
    return server;
  }

  public void onStart() {
    Bundle result = new Bundle();
    Store store = new Store(getTargetContext());
    Supplier<ClubClient> original = BookingService.clients;
    Activity screen = null;
    try {
      Server success = prepare(store, "success");
      store.enabled(true);
      check(store.jobs().size() == 1 && store.jobs().get(0).release.isBefore(Instant.now().minusSeconds(90)),
          "late Pilates job remains pending after the old 90-second cutoff");
      getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      Scheduler.refresh(getTargetContext());
      awaitResult(store, "Reservada");
      check(store.lastWake() > 0, "scheduler's exact alarm starts the actual background service");
      check(store.lastAlarm() > 0, "alarm delivery is recorded separately from service activation");
      check(success.logins == 1, "background service logs in with stored credentials");
      check(success.sends == 1, "included membership class sends the reserve-place action once");
      check(success.postReads >= 1, "service verifies inscription in a separate authenticated calendar");
      check(store.history().get(0)[3].contains("consulta posterior"), "verified result persisted in history");
      check(store.jobs().isEmpty(), "confirmed class cannot be scheduled a second time");
      boolean notified = false;
      for (android.service.notification.StatusBarNotification n : getTargetContext()
          .getSystemService(NotificationManager.class).getActiveNotifications())
        if (n.getId() == 2 && "¡Plaza reservada!".contentEquals(
            n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE, ""))) notified = true;
      check(notified, "confirmed booking posts the success notification");

      Server uncertain = prepare(store, "uncertain");
      // Remaining cases use a visible Activity to respect foreground-service launch policy.
      screen = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      store.enabled(true);
      getTargetContext().startForegroundService(new Intent(getTargetContext(), BookingService.class));
      awaitResult(store, "Comprobar en la web");
      check(uncertain.sends == 1 && uncertain.postReads >= 1, "lost response causes a verification read and no resend");
      check(store.jobs().isEmpty(), "uncertain mutation stays blocked from automatic catch-up");

      Server recovered = prepare(store, "lost-confirmation");
      store.enabled(true);
      getTargetContext().startForegroundService(new Intent(getTargetContext(), BookingService.class));
      awaitResult(store, "Reservada");
      check(recovered.sends == 1 && recovered.postReads >= 1,
          "service recovers an accepted booking after the response is lost");

      Server failure = prepare(store, "login-failure");
      store.enabled(true);
      getTargetContext().startForegroundService(new Intent(getTargetContext(), BookingService.class));
      awaitResult(store, "No reservada");
      check(failure.logins == 1 && failure.sends == 0, "failed login records an error without sending a reservation");
      result.putString("stream", report + "RESULT: " + checks
          + " Android booking flow checks passed; simulated HTTP, no live reservations.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putString("stream", report + "FAIL " + error + "\n" + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      store.enabled(false);
      Scheduler.refresh(getTargetContext());
      getTargetContext().stopService(new Intent(getTargetContext(), BookingService.class));
      BookingService.clients = original;
      if (screen != null) {
        Activity last = screen;
        runOnMainSync(last::finish);
      }
    }
  }
}
