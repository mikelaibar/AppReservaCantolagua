package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BookingService extends Service {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private volatile boolean stopped;
    public IBinder onBind(Intent i){return null;}
    public int onStartCommand(Intent intent,int flags,int startId){
        Notification n=Notices.notification(this,"Preparando las reservas","Conectando con Cantolagua. Puedes cerrar la aplicación");
        if(Build.VERSION.SDK_INT>=29)startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(1,n);
        if(RUNNING.compareAndSet(false,true))new Thread(this::run,"cantolagua-booking").start();return START_NOT_STICKY;
    }
    private boolean active(Store s,String revision){return !stopped&&s.enabled()&&revision.equals(s.revision());}
    private void run(){
        Store store=new Store(this);PowerManager.WakeLock wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MiCantolagua:booking");wake.acquire(180000);
        String account=store.account(),revision=store.revision();long start=SystemClock.elapsedRealtime();List<Plan.Job> batch=new ArrayList<>();long clockStart=System.currentTimeMillis();
        try{
            if(!active(store,revision))return;List<Plan.Job> pending=store.jobs();if(pending.isEmpty())return;Instant release=pending.get(0).release;
            if(release.isAfter(Instant.now().plusSeconds(70)))return;
            for(Plan.Job j:pending)if(j.release.equals(release))batch.add(j);
            ClubClient client=new ClubClient();client.login(store.user(),store.password());
            long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
            while(Instant.now().isBefore(release)){
                if(!active(store,revision)||SystemClock.elapsedRealtime()-start>150000)return;
                if(Math.abs((System.currentTimeMillis()-wall)-(SystemClock.elapsedRealtime()-elapsed))>3000)throw new ClubClient.Problem("El reloj del teléfono ha cambiado durante la preparación");Thread.sleep(Math.min(250,Math.max(1,release.toEpochMilli()-System.currentTimeMillis())));
            }
            for(Plan.Job job:batch){
                if(!active(store,revision)||!store.desired(job)||SystemClock.elapsedRealtime()-start>150000)continue;ClubClient.Session session=null;String detail="La clase aún no está publicada o no se ha abierto";
                while(active(store,revision)&&SystemClock.elapsedRealtime()-start<150000&&Instant.now().isBefore(release.plusSeconds(90))){
                    try{session=ClubClient.match(client.catalogue(),job);if(session!=null&&(session.booked||session.waitlisted||session.open||session.status.equals("Completa")||session.paid))break;if(session!=null)detail=session.status;}catch(Exception e){detail="No se pudo consultar la clase. Comprueba la conexión y el acceso";}
                    Thread.sleep(2000);
                }
                if(!active(store,revision)||!store.desired(job))continue;
                if(session==null||!session.open){if(store.claim(account,job,null)){String state=session!=null?session.status:"No reservada";store.finish(account,job,state,session!=null?"Estado consultado en Cantolagua. No se ha enviado una reserva":detail);Notices.result(this,state,job.label());}continue;}
                synchronized(Store.LOCK){if(!active(store,revision)||!store.desired(job)||!Instant.now().isBefore(release.plusSeconds(90))||SystemClock.elapsedRealtime()-start>150000||Math.abs((System.currentTimeMillis()-clockStart)-(SystemClock.elapsedRealtime()-start))>3000)continue;if(!store.claim(account,job,session.id))continue;}
                
                try{
                    boolean confirmed=client.reserve(session);
                    if(!confirmed){try{ClubClient.Session check=ClubClient.match(client.catalogue(),job);confirmed=check!=null&&check.booked;}catch(Exception ignored){}}
                    store.finish(account,job,confirmed?"Reservada":"Comprobar en la web",confirmed?"Cantolagua ha confirmado tu plaza":"La respuesta no confirma la plaza. Revisa la web antes de repetir");Notices.result(this,confirmed?"¡Plaza reservada!":"Comprueba tu reserva",job.label());
                }catch(Exception e){store.finish(account,job,"Comprobar en la web","La conexión o la respuesta falló tras iniciar la solicitud. No se repetirá automáticamente");Notices.result(this,"Comprueba tu reserva",job.label()+". Abre Cantolagua para verificar si tienes plaza");}
            }
        }catch(Exception e){
            for(Plan.Job job:batch)if(!store.attempted(job.key())&&store.claim(account,job,null))store.finish(account,job,"No reservada","No se pudo preparar la conexión. Revisa tus credenciales y la conexión a Internet");
            Notices.result(this,"No se pudo completar la reserva","Revisa el resultado en la aplicación y comprueba tu acceso a Cantolagua");
        }finally{
            if(wake.isHeld())wake.release();RUNNING.set(false);try{Scheduler.refresh(this);}catch(RuntimeException ignored){}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }
    public void onTimeout(int startId,int type){stopped=true;stopSelf();}
    public void onDestroy(){stopped=true;super.onDestroy();}
}
