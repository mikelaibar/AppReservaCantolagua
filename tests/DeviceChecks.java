package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.os.*;
import java.time.*;
import java.util.*;

/** Runs only on an isolated emulator. Uses synthetic credentials and sends no web requests. */
public final class DeviceChecks extends Instrumentation {
  int count;
  StringBuilder report = new StringBuilder();

  private void check(boolean result, String label) {
    if (!result) throw new AssertionError(label);
    count++;
    report.append("PASS ").append(label).append('\n');
  }

  private android.view.View find(android.view.View view, String text) {
    if (view instanceof android.widget.TextView
        && text.contentEquals(((android.widget.TextView) view).getText())) return view;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View found = find(group.getChildAt(i), text);
        if (found != null) return found;
      }
    }
    return null;
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  public void onStart() {
    Bundle result = new Bundle();
    Store s = new Store(getTargetContext());
    try {
      s.erase();
      check(s.lead() == 4, "confirmed four-day rule is the installation default");
      s.credentials("device-test", "synthetic-password");
      check("synthetic-password".equals(s.password()), "Keystore encrypted credential round trip");
      check(!s.enabled() && s.verified(), "new credentials pause automation");
      s.close();
      s = new Store(getTargetContext());
      check("synthetic-password".equals(s.password()), "credentials survive store reopening");
      Plan.Slot slot = new Plan.Slot(1, "8", "Clase de prueba", "20:30");
      LocalDate week = Plan.monday(LocalDate.now(Plan.ZONE));
      s.slots(week, Collections.singletonList(slot), true);
      check(s.template().size() == 1, "weekly template saved");
      s.slots(week, Collections.emptyList(), false);
      check(
          s.week(week).isEmpty() && s.week(week.plusWeeks(1)).size() == 1,
          "one-week override preserves future template");
      s.resetWeek(week);
      check(s.week(week).size() == 1, "reset week restores template");
      Plan.Job j = new Plan.Job(slot, week.plusDays(1), 4);
      String account = s.account();
      check(s.claim(account, j, "947"), "durable claim created before mutation");
      check(!s.claim(account, j, "947"), "duplicate job claim blocked");
      Plan.Job other = new Plan.Job(slot, j.date.plusWeeks(1), 4);
      check(!s.claim(account, other, "947"), "duplicate canonical session claim blocked");
      check(
          s.attempted(j.key()) && s.history().get(0)[2].equals("Comprobar en la web"),
          "interrupted mutation remains uncertain");
      s.finish(account, j, "Reservada", "Fixture");
      check(s.history().get(0)[2].equals("Reservada"), "confirmed outcome persisted");
      Plan.Job retry = new Plan.Job(slot, LocalDate.now(Plan.ZONE).plusDays(5), 4);
      check(s.claim(account, retry, "950"), "synthetic failed attempt recorded");
      s.finish(account, retry, "Requiere pago", "Synthetic previous result");
      check(s.retryAfterReview(retry.key()), "review permits retry before its deadline");
      check(s.history().stream().anyMatch(h -> h[0].equals(retry.key())
          && h[2].equals(Store.RETRY_READY)), "reviewed retry remains visible in history");
      check(!s.attempted(retry.key()), "reviewed retry becomes eligible for the scheduler");
      Store reopened = new Store(getTargetContext());
      check(reopened.history().stream().anyMatch(h -> h[0].equals(retry.key())
          && h[2].equals(Store.RETRY_READY)), "pending retry survives reopening the database");
      reopened.close();
      check(s.claim(account, retry, "950"), "retry reclaims the existing history row");
      check(!s.claim(account, retry, "950"), "retry cannot submit the same claim twice");
      s.finish(account, retry, "Reservada", "Synthetic confirmation");
      check(!s.retryAfterReview(retry.key()), "confirmed reservation cannot be reopened");
      Plan.Job expired = new Plan.Job(slot, LocalDate.now(Plan.ZONE).plusDays(3), 4);
      check(s.claim(account, expired, null), "synthetic expired attempt recorded");
      s.finish(account, expired, "Requiere pago", "Synthetic expired result");
      check(!s.retryAfterReview(expired.key()), "review rejects a retry after the deadline");
      check(s.attempted(expired.key()) && s.history().stream().anyMatch(h ->
          h[0].equals(expired.key()) && h[2].equals("Requiere pago")
              && h[3].equals("Synthetic expired result")),
          "expired review preserves the original result and duplicate protection");
      check(Scheduler.allowed(getTargetContext()), "exact alarm permission granted for test");
      s.enabled(true);
      Scheduler.refresh(getTargetContext());
      PendingIntent pi =
          PendingIntent.getBroadcast(
              getTargetContext(),
              1,
              new Intent(getTargetContext(), AlarmReceiver.class),
              PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
      check(pi != null, "scheduler creates private immutable alarm intent");
      s.enabled(false);
      Scheduler.refresh(getTargetContext());
      s.erase();
      s.enabled(true);
      long before = System.currentTimeMillis();
      getTargetContext()
          .startActivity(
              new Intent(Intent.ACTION_MAIN)
                  .addCategory(Intent.CATEGORY_HOME)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      AlarmManager alarm = getTargetContext().getSystemService(AlarmManager.class);
      PendingIntent wake =
          PendingIntent.getBroadcast(
              getTargetContext(),
              77,
              new Intent(getTargetContext(), AlarmReceiver.class),
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, before + 4000, wake);
      for (int n = 0; n < 40 && s.lastWake() < before; n++) Thread.sleep(500);
      check(s.lastWake() >= before, "exact alarm starts foreground service while app UI is closed");
      s.enabled(false);
      Scheduler.refresh(getTargetContext());
      s.erase();
      // Keep this deterministic UI check offline; live HTTP has its own opt-in test.
      ClubClient.Session fixture = new ClubClient.Session();
      fixture.date = LocalDate.now(Plan.ZONE);
      fixture.time = "09:20";
      fixture.activity = "14";
      fixture.name = "Step-Training";
      s.catalogue(Collections.singletonList(fixture));
      s.claim(s.account(), expired, null);
      s.finish(s.account(), expired, "No reservada", "Synthetic expired UI result");
      Intent launch =
          new Intent(getTargetContext(), MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      Activity activity = startActivitySync(launch);
      check(activity != null, "release activity launches on Android 15");
      final Activity screen = activity;
      runOnMainSync(() -> find(screen.getWindow().getDecorView(), "Ajustes").performClick());
      check(
          find(screen.getWindow().getDecorView(), "Antelación: 4 días antes") != null,
          "settings display confirmed booking rule");
      runOnMainSync(() -> find(screen.getWindow().getDecorView(), "Reservas").performClick());
      check(
          find(screen.getWindow().getDecorView(), "Próximos intentos") != null,
          "history tab opens without credentials");
      check(find(screen.getWindow().getDecorView(),
          "El margen de reintento de 90 segundos desde la apertura ya ha pasado."
              + " Este resultado se conserva. Comprueba la plaza o la lista de espera en la web.")
          != null, "expired history explains why another attempt cannot run");
      check(find(screen.getWindow().getDecorView(),
          "He revisado la web: permitir otro intento") == null,
          "expired result does not offer the destructive old retry action");
      runOnMainSync(() -> find(screen.getWindow().getDecorView(), "Mi semana").performClick());
      check(
          find(screen.getWindow().getDecorView(), "Todas las semanas") != null
              || find(screen.getWindow().getDecorView(), "✓ Todas las semanas") != null,
          "weekly plan tab opens");
      result.putString(
          "stream",
          report + "RESULT: " + count + " device checks passed; no live requests sent.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString(
          "stream",
          report + "FAIL " + e.toString() + "\n" + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
