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
