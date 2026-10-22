package es.cantolagua.misemana;

import android.content.*;

public final class AlarmReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent intent) {
    Store s = new Store(c);
    s.alarmFired();
    if (!s.enabled()) return;
    try {
      c.startForegroundService(new Intent(c, BookingService.class));
    } catch (RuntimeException e) {
      s.enabled(false);
      s.stage("Android bloqueó el inicio del servicio en segundo plano");
      s.status("Android no permitió iniciar la reserva. Abre la app y revisa los permisos");
      Scheduler.refresh(c);
      Notices.result(
          c,
          "Reserva no iniciada",
          "Android ha bloqueado el inicio en segundo plano. Revisa los permisos");
    }
  }
}
