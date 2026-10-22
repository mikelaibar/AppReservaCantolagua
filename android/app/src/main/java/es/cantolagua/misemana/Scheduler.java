package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
import java.util.*;

public final class Scheduler {
  public static boolean allowed(Context c) {
    return Build.VERSION.SDK_INT < 31
        || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
  }

  public static void refresh(Context c) {
    synchronized (Store.LOCK) {
      Store s = new Store(c);
      AlarmManager am = c.getSystemService(AlarmManager.class);
      PendingIntent pi =
          PendingIntent.getBroadcast(
              c,
              1,
              new Intent(c, AlarmReceiver.class),
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      am.cancel(pi);
      s.scheduled(0);
      if (!s.enabled()) return;
      if (!allowed(c)) {
        s.enabled(false);
        s.status("Activa Alarmas y recordatorios en Ajustes y vuelve a activar las reservas");
        return;
      }
      List<Plan.Job> jobs = s.jobs();
      if (jobs.isEmpty()) {
        s.status("No hay clases pendientes. Revisa tu horario semanal");
        return;
      }
      Plan.Job next = jobs.get(0);
      long when = Math.max(System.currentTimeMillis() + 1500, next.release.toEpochMilli() - 60000);
      try {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        s.scheduled(when);
        s.status(next.release.isBefore(Instant.now())
            ? "Recuperando una clase ya abierta: " + next.label()
            : "Próximo intento: "
                + next.release
                    .atZone(Plan.ZONE)
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm"))
                + " · hora de Madrid");
      } catch (SecurityException e) {
        s.enabled(false);
        s.status("Android ha retirado el permiso de alarmas exactas");
      }
    }
  }
}
