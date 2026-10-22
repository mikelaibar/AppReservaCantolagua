package es.cantolagua.misemana;

import java.io.IOException;
import java.time.*;
import java.util.*;

/** Exercises the actual login/reserve/verification client with a simulated club. */
public final class BookingFlowTests {
  static int checks;

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
    System.out.println("PASS " + message);
  }

  static final class Server implements ClubClient.Transport {
    boolean loggedIn, booked, timeout, accept = true, stale, verificationFails, full, waitlisted;
    int sends, calendars;

    String calendar() {
      return "<body class='identificado'><select id='IdInscrito'>"
          + "<option value='99' selected>Persona de prueba</option></select><div id='cuadricula'>"
          + "<div class='Clases_Clase Actividad7 Clase__Clicable "
          + (booked && !stale ? "Clase__Inscrito" : "")
          + (waitlisted ? " Clase__EnListaEspera" : full ? " Clase__ListaEspera" : "")
          + "' id='Clase_641' data-fecha='lunes 26/10'"
          + " data-importe='Incluida en Abono Actividades'>"
          + "<span class='hinicio_clase'>9:00</span><div class='Clase_Actividad'>"
          + "<a href='r.aspx?a=" + (booked && !stale ? "borrardeclase"
              : full ? "apuntarlistaesperaclase" : "d")
          + "&amp;R=641'>Pilates</a></div></div></div></body>";
    }

    public String send(String url, Map<String, String> fields, boolean mutation) throws Exception {
      if (url.contains("login.aspx")) {
        if (fields == null) return "<form id='frmLogin'></form>";
        if (!"test-user".equals(fields.get("mail"))
            || !"test-password".equals(fields.get("clave"))) throw new AssertionError("Login fields");
        loggedIn = true;
        return "<body>Inicio</body>";
      }
      if (!loggedIn) throw new AssertionError("Reservation flow did not log in first");
      if (mutation) {
        sends++;
        Map<String, String> q = ClubClient.query(new java.net.URI(url));
        if (!(full ? "apuntarlistaesperaclase" : "d").equals(q.get("a")) || !"641".equals(q.get("R"))
            || !"99".equals(q.get("IdInscrito"))) throw new AssertionError("Wrong action/session/person");
        if (full) waitlisted = accept;
        else booked = accept;
        if (timeout) throw new IOException("Simulated lost response");
        return "{\"code\":\"OK\",\"result\":{\"asistenciasCreadas\":[{\"clase\":641}]}}";
      }
      calendars++;
      if (verificationFails && sends > 0) throw new IOException("Read unavailable");
      return calendar();
    }

    ClubClient client() throws Exception {
      ClubClient client = new ClubClient(this, LocalDate.of(2026, 10, 22));
      client.login("test-user", "test-password");
      return client;
    }
  }

  public static void main(String[] args) throws Exception {
    Plan.Job job = new Plan.Job(new Plan.Slot(0, "7", "Pilates", "09:00"),
        LocalDate.of(2026, 10, 26), 4);
    Server server = new Server();
    ClubClient client = server.client();
    ClubClient.Session session = ClubClient.match(client.catalogue(), job);
    check(session != null && session.open, "Monday 26 Pilates 09:00 resolves the real session contract");
    check(client.reserveVerified(session, job).confirmed, "login, direct request and fresh calendar confirm the place");
    check(server.sends == 1 && server.calendars >= 3, "one send and a separate post-booking verification");
    check(ClubClient.match(client.catalogue(), job).booked, "fresh calendar identifies the user's inscription");

    server = new Server(); server.timeout = true;
    client = server.client();
    check(client.reserveVerified(ClubClient.match(client.catalogue(), job), job).confirmed,
        "lost booking response recovered by reading the confirmed inscription");
    check(server.sends == 1, "timeout recovery reads instead of sending again");

    server = new Server(); server.timeout = true; server.accept = false;
    client = server.client();
    check(!client.reserveVerified(ClubClient.match(client.catalogue(), job), job).confirmed,
        "lost response without inscription remains uncertain");
    check(server.sends == 1, "uncertain result never triggers an automatic resend");

    server = new Server(); server.stale = true;
    client = server.client();
    check(!client.reserveVerified(ClubClient.match(client.catalogue(), job), job).confirmed,
        "success receipt contradicted by calendar requires user review");

    server = new Server(); server.verificationFails = true;
    client = server.client();
    check(client.reserveVerified(ClubClient.match(client.catalogue(), job), job).confirmed,
        "specific booking receipt remains evidence when follow-up read is unavailable");

    server = new Server(); client = server.client();
    boolean rejected = false;
    try {
      client.reserveVerified(ClubClient.match(client.catalogue(), job),
          new Plan.Job(new Plan.Slot(0, "7", "Pilates", "18:30"), job.date, 4));
    } catch (ClubClient.Problem expected) { rejected = true; }
    check(rejected && server.sends == 0, "mismatched requested hour rejected before any mutation");
    server = new Server(); server.full = true; client = server.client();
    ClubClient.Outcome waiting = client.joinWaitlistVerified(ClubClient.match(client.catalogue(), job), job);
    check(waiting.confirmed && waiting.waitlisted && server.sends == 1,
        "explicit waitlist action is verified against the authenticated calendar");
    client.joinWaitlistVerified(ClubClient.match(client.catalogue(), job), job);
    check(server.sends == 1, "existing waiting-list inscription is never added twice");
    server = new Server(); server.full = true; server.timeout = true; client = server.client();
    check(client.joinWaitlistVerified(ClubClient.match(client.catalogue(), job), job).waitlisted
        && server.sends == 1, "lost waitlist response is recovered by reading, without resend");
    server = new Server(); server.full = true; server.timeout = true; server.accept = false;
    client = server.client();
    check(!client.joinWaitlistVerified(ClubClient.match(client.catalogue(), job), job).confirmed
        && server.sends == 1, "uncertain waitlist request stays unconfirmed and is not replayed");
    server = new Server(); client = server.client(); rejected = false;
    try { client.joinWaitlistVerified(ClubClient.match(client.catalogue(), job), job); }
    catch (ClubClient.Problem expected) { rejected = true; }
    check(rejected && server.sends == 0, "waitlist action refuses a direct-booking link");
    System.out.println("RESULT: " + checks + " booking flow checks passed; simulated club only.");
  }
}
