package es.cantolagua.misemana;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.jsoup.Jsoup;

public final class FutureTimetableTests {
  static int count;
  static void check(boolean ok, String label) {
    if (!ok) throw new AssertionError(label);
    count++;
    System.out.println("PASS " + label);
  }
  static ClubClient.Session find(List<ClubClient.Session> sessions, String id) {
    return sessions.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null);
  }
  public static void main(String[] args) throws Exception {
    String html = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
    LocalDate today = LocalDate.of(2026, 10, 22);
    List<ClubClient.Session> sessions = ClubClient.parse(Jsoup.parse(html), today);
    ClubClient.Session fitball = find(sessions, "1358");
    check(fitball != null && fitball.name.equals("Fitball") && fitball.activity.equals("13")
        && fitball.time.equals("16:30") && fitball.date.equals(LocalDate.of(2026, 10, 26)),
        "reported Fitball Monday 26 at 16:30 is read before reservations open");
    check(!fitball.open && fitball.href.isEmpty() && fitball.status.equals("Aún no disponible"),
        "future timetable card is visible but cannot send a booking");
    ClubClient.Session wednesday = find(sessions, "1392");
    check(wednesday != null && wednesday.time.equals("17:30")
        && wednesday.date.equals(LocalDate.of(2026, 10, 28)),
        "Wednesday Fitball stays in its own column and hour");
    ClubClient.Session tuesday = find(sessions, "1430");
    check(tuesday != null && tuesday.time.equals("09:20")
        && tuesday.date.equals(LocalDate.of(2026, 10, 27)),
        "future Tuesday uses 09:20 from the card, not the 09:00 table row");
    List<Timetable.Entry> entries = Timetable.merge(Collections.emptyList(), sessions);
    check(Timetable.label(entries, 0, "16:30").equals("16:30 · Fitball"),
        "Monday selector offers the actual future Fitball name");
    check(Timetable.choices(entries, 2, "17:30").stream().anyMatch(s -> s.name.equals("Fitball")),
        "parallel future activities remain selectable on Wednesday");
    int[] sends = {0};
    ClubClient client = new ClubClient((url, fields, mutation) -> { sends[0]++; return ""; });
    boolean refused = false;
    try { client.reserve(fitball); } catch (ClubClient.Problem expected) { refused = true; }
    check(refused && sends[0] == 0, "a future preview never performs a network mutation");
    check(find(ClubClient.parse(Jsoup.parse(html.replace("Lun. 26", "Mar. 26")), today), "1358") == null,
        "invalid weekday header cannot assign Fitball to an invented date");
    check(find(ClubClient.parse(Jsoup.parse(html.replace("<th>", "<th colspan='2'>")), today), "1358") == null,
        "unsupported spanning columns do not shift activities to another day");
    String card = "<div class='Clases_Clase Actividad13 Clase__Futura' id='Clase_9000'>"
        + "<span class='hinicio_clase'>16:30</span><div class='Clase_Actividad'>Fitball</div></div>";
    String mobile = "<div id='cuadricula'><div class='Clases_EncabezadoDia Clases_DiaMostrado'>"
        + "<div class='Clases_Dia'><a href='/reservas/actividades.aspx?dia=2026-10-26'>Lun. 26</a>"
        + "</div></div><div class='Clases_HorarioDia'>" + card + "</div></div>";
    check(find(ClubClient.parse(Jsoup.parse(mobile), today), "9000").date.equals(LocalDate.of(2026, 10, 26)),
        "mobile future card uses the validated selected date tab");
    check(find(ClubClient.parse(Jsoup.parse(mobile.replace("Lun. 26", "Mar. 26")), today), "9000") == null,
        "mobile header inconsistent with its date link is not used");
    String rollover = "<div id='cuadricula'><table class='Clases'><thead><tr><td>Hora</td>"
        + "<th><div class='Clases_Dia'>Vie. 1</div></th></tr></thead><tbody><tr><th>16:00</th><td>"
        + card + "</td></tr></tbody></table></div>";
    check(find(ClubClient.parse(Jsoup.parse(rollover), LocalDate.of(2026, 12, 30)), "9000")
        .date.equals(LocalDate.of(2027, 1, 1)), "future headers resolve month and year rollover");
    check(find(ClubClient.parse(Jsoup.parse("<div id='cuadricula'>" + card + "</div>"), today), "9000") == null,
        "missing day context does not produce a guessed timetable entry");
    System.out.println("RESULT: " + count + " future timetable checks passed; no live bookings.");
  }
}
