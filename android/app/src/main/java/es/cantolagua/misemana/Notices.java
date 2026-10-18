package es.cantolagua.misemana;
import android.app.*;
import android.content.*;
public final class Notices {
    static Notification notification(Context c,String title,String text){NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("bookings","Reservas de Cantolagua",NotificationManager.IMPORTANCE_DEFAULT));PendingIntent tap=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(c,"bookings").setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(tap).setAutoCancel(true).build();}
    static void result(Context c,String title,String detail){try{c.getSystemService(NotificationManager.class).notify(2,notification(c,title,detail));}catch(SecurityException ignored){}}
}
