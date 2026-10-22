package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.*;
import org.jsoup.Jsoup;

/** Isolated emulator only; actual native picker with the public future-card fixture. */
public final class FutureCalendarChecks extends CalendarDeviceChecks {
  public void onStart() {
    Bundle result = new Bundle();
    Store store = new Store(getTargetContext());
    try {
      store.erase();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream input = getContext().getAssets().open("calendar-future-oct22.html")) {
        byte[] b = new byte[8192]; int n;
        while ((n = input.read(b)) != -1) bytes.write(b, 0, n);
      }
      store.catalogue(ClubClient.parse(Jsoup.parse(bytes.toString("UTF-8")), LocalDate.of(2026,10,22)));
      Activity screen = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      Field field = MainActivity.class.getDeclaredField("picker"); field.setAccessible(true);
      int[] days = {0,2}; String[] times = {"16:30","17:30"};
      for (int k = 0; k < days.length; k++) {
        final int day = days[k]; final String time = times[k];
        runOnMainSync(() -> {
          View label = find(screen.getWindow().getDecorView(), Plan.DAYS[day]);
          find((View)label.getParent(), "+ Añadir actividad").performClick();
        });
        waitForIdleSync();
        AlertDialog picker = (AlertDialog)field.get(screen);
        check(picker.isShowing(), "native future picker opens for " + Plan.DAYS[day]);
        ListView list = picker.getListView(); int selected = -1;
        for (int n = 0; n < list.getAdapter().getCount(); n++) {
          String label = list.getAdapter().getItem(n).toString();
          if (label.startsWith(time + " · ") && label.contains("Fitball")) selected = n;
        }
        check(selected >= 0, "native selector offers future Fitball " + Plan.DAYS[day] + " " + time);
        final int choice = selected;
        runOnMainSync(() -> list.performItemClick(null, choice, list.getAdapter().getItemId(choice)));
        waitForIdleSync();
        picker = (AlertDialog)field.get(screen);
        if (picker.isShowing()) {
          ListView activities = picker.getListView(); int fitball = -1;
          for (int n = 0; n < activities.getAdapter().getCount(); n++)
            if (activities.getAdapter().getItem(n).toString().equals(time + " · Fitball")) fitball = n;
          check(fitball >= 0, "parallel activities allow selecting Fitball specifically");
          final int activity = fitball;
          runOnMainSync(() -> activities.performItemClick(null, activity, activities.getAdapter().getItemId(activity)));
          waitForIdleSync();
        }
        check(store.template().stream().anyMatch(slot -> slot.day == day && slot.time.equals(time)
            && slot.activity.equals("13") && slot.name.equals("Fitball")),
            "future selection persists exact weekday, clock and activity " + Plan.DAYS[day]);
      }
      check(store.user().isEmpty(), "future catalogue selection requires no saved account");
      result.putString("stream", report + "RESULT: " + passed + " future selector checks passed; no bookings.\n");
      finish(Activity.RESULT_OK,result);
    } catch (Throwable e) {
      result.putString("stream", report + "FAIL " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED,result);
    } finally { try { store.erase(); } catch (Exception ignored) {} }
  }
}
