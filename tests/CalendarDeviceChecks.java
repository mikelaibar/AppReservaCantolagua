package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import org.jsoup.Jsoup;

/** Isolated emulator only. Exercises the actual weekday buttons and picker callbacks. */
public final class CalendarDeviceChecks extends Instrumentation {
  int passed;
  boolean expectUpgrade;
  StringBuilder report = new StringBuilder();

  void check(boolean good, String label) {
    if (!good) throw new AssertionError(label);
    passed++;
    report.append("PASS ").append(label).append('\n');
  }

  View find(View v, String label) {
    if (v instanceof TextView && label.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int n = 0; n < g.getChildCount(); n++) {
        View found = find(g.getChildAt(n), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  public void onCreate(Bundle args) {
    super.onCreate(args);
    expectUpgrade = "true".equals(args.getString("expectUpgrade"));
    start();
  }

  public void onStart() {
    Bundle result = new Bundle();
    Store store = new Store(getTargetContext());
    try {
      if (expectUpgrade) {
        check(
            store.user().equals("upgrade-test")
                && store.password().equals("synthetic-upgrade-password"),
            "updating 1.0.0 to 1.0.1 preserves encrypted credentials");
        check(
            store.template().size() == 1
                && store.template().get(0).toString().equals("09:20 · Step-Training"),
            "updating APK preserves selected weekday and hour");
      }
      store.erase();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream input = getContext().getAssets().open("calendar-week.html")) {
        byte[] b = new byte[8192];
        int n;
        while ((n = input.read(b)) != -1) bytes.write(b, 0, n);
      }
      List<ClubClient.Session> sessions =
          ClubClient.parse(Jsoup.parse(bytes.toString("UTF-8")), LocalDate.of(2026, 10, 18));
      store.catalogue(sessions);
      ClubClient.Session monday = new ClubClient.Session();
      monday.date = LocalDate.of(2026, 10, 26);
      monday.time = "09:00";
      monday.activity = "7";
      monday.name = "Pilates";
      store.catalogue(Collections.singletonList(monday));
      check(
          store.catalogue().size() == 26,
          "Monday-only refresh preserves the rest of the weekly timetable on Android");
      Activity screen =
          startActivitySync(
              new Intent(getTargetContext(), MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      Field field = MainActivity.class.getDeclaredField("picker");
      field.setAccessible(true);
      String[] expected = {
        "09:00 · Pilates", "09:20 · Step-Training", "09:00 · Movilidad", "09:20 · Step-Training"
      };
      for (int day = 0; day < 4; day++) {
        final int selectedDay = day;
        runOnMainSync(
            () -> {
              View label = find(screen.getWindow().getDecorView(), Plan.DAYS[selectedDay]);
              find((View) label.getParent(), "+ Añadir actividad").performClick();
            });
        waitForIdleSync();
        AlertDialog picker = (AlertDialog) field.get(screen);
        check(
            picker != null && picker.isShowing(),
            "weekday button opens hour picker for " + Plan.DAYS[day]);
        ListView list = picker.getListView();
        if (day == 1) {
          android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
          if (screenshot != null) {
            try (FileOutputStream output =
                new FileOutputStream(
                    new File(getTargetContext().getFilesDir(), "tuesday-picker.png"))) {
              screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            }
            screenshot.recycle();
          }
        }

        check(
            expected[day].equals(list.getAdapter().getItem(0).toString()),
            "first option for " + Plan.DAYS[day] + " is " + expected[day]);
        boolean allHours = true;
        for (int n = 0; n < list.getAdapter().getCount(); n++)
          if (!list.getAdapter().getItem(n).toString().matches("[0-2][0-9]:[0-5][0-9] · .+"))
            allHours = false;
        check(allHours, "every option shows a clock for " + Plan.DAYS[day]);
        runOnMainSync(
            () -> list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0)));
        waitForIdleSync();
        check(
            store.template().stream()
                .anyMatch(
                    slot ->
                        slot.day == selectedDay && slot.toString().equals(expected[selectedDay])),
            "picker saves the actual activity and hour under " + Plan.DAYS[day]);
      }
      runOnMainSync(
          () -> {
            View label = find(screen.getWindow().getDecorView(), "Viernes");
            find((View) label.getParent(), "+ Añadir actividad").performClick();
          });
      waitForIdleSync();
      AlertDialog friday = (AlertDialog) field.get(screen);
      check(
          friday
              .getListView()
              .getAdapter()
              .getItem(0)
              .toString()
              .equals("09:00 · Actividad pendiente de consultar"),
          "unpublished Friday offers hours without inventing Pilates");
      runOnMainSync(friday::dismiss);
      result.putString(
          "stream",
          report + "RESULT: " + passed + " calendar device checks passed; no web requests.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", report + "FAIL " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      try {
        store.erase();
      } catch (Exception ignored) {
      }
    }
  }
}
