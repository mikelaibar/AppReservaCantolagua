package es.cantolagua.misemana;
import android.content.*;
public final class SystemReceiver extends BroadcastReceiver {public void onReceive(Context c,Intent i){try{Scheduler.refresh(c);}catch(RuntimeException ignored){new Store(c).status("Abre la aplicación para revisar la programación");}}}
