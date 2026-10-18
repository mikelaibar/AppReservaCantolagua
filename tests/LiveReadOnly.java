package es.cantolagua.misemana;
import java.util.*;
/** Optional read-only diagnostic. Credentials are read from the terminal, never command arguments. */
public final class LiveReadOnly {
    public static void main(String[] args)throws Exception{
        java.io.Console console=System.console();if(console==null)throw new IllegalStateException("Run from a private interactive terminal");
        String user=console.readLine("Usuario: ");char[] chars=console.readPassword("Contraseña: ");
        ClubClient client=new ClubClient();try{client.login(user,new String(chars));}finally{Arrays.fill(chars,'\0');}
        List<ClubClient.Session> sessions=client.catalogue();int booked=0,waiting=0,open=0;for(ClubClient.Session s:sessions){if(s.booked)booked++;if(s.waitlisted)waiting++;if(s.open)open++;}
        System.out.println("Acceso confirmado. Clases publicadas: "+sessions.size()+"; ya reservadas: "+booked+"; en espera: "+waiting+"; disponibles: "+open+". No se ha enviado ninguna reserva.");
    }
}
