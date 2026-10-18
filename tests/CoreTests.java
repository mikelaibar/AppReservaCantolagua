package es.cantolagua.misemana;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;

public final class CoreTests {
  static int count;

  static void check(boolean ok, String name) {
    if (!ok) throw new AssertionError(name);
    count++;
    System.out.println("PASS " + name);
  }

  interface Action {
    void run() throws Exception;
  }

  static void rejects(Action a, String name) throws Exception {
    boolean rejected = false;
    try {
      a.run();
    } catch (Exception e) {
      rejected = true;
    }
    check(rejected, name);
  }

  public static void main(String[] args) throws Exception {
    String html = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
    LocalDate today = LocalDate.of(2026, 10, 18);
    List<ClubClient.Session> sessions = ClubClient.parse(Jsoup.parse(html), today);
    ClubClient.Session item = sessions.get(0);
    check(
        item.date.equals(LocalDate.of(2026, 10, 20)) && item.open && item.person.equals("99"),
        "parse real calendar contract");
    Plan.Slot slot = new Plan.Slot(1, "8", "Global Training", "20:30");
    Plan.Job job = new Plan.Job(slot, LocalDate.of(2026, 10, 20), 4);
    check(ClubClient.match(sessions, job) == item, "exact date activity and time match");
    check(
        ClubClient.match(
                sessions, new Plan.Job(new Plan.Slot(1, "7", "Pilates", "20:30"), job.date, 4))
            == null,
        "never substitutes a different activity");
    List<ClubClient.Session> duplicate = new ArrayList<>(sessions);
    duplicate.add(item);
    rejects(() -> ClubClient.match(duplicate, job), "ambiguous room/session rejected");
    check(
        !ClubClient.parse(Jsoup.parse(html.replace("a=d&amp;", "a=borrardeclase&amp;")), today)
            .get(0)
            .open,
        "cancel URL never booked");
    check(
        !ClubClient.parse(
                Jsoup.parse(html.replace("Clase__Clicable", "Clase__ListaEspera Clase__Clicable")),
                today)
            .get(0)
            .open,
        "full class never booked");
    check(
        !ClubClient.parse(
                Jsoup.parse(html.replace("Clase__Clicable", "Clase__Futura Clase__Clicable")),
                today)
            .get(0)
            .open,
        "future class never booked");
    check(
        !ClubClient.parse(
                Jsoup.parse(
                    html.replace("id=\"Clase_947\"", "data-importe=\"3,50\" id=\"Clase_947\"")),
                today)
            .get(0)
            .open,
        "payment blocks automation");
    check(
        !ClubClient.parse(Jsoup.parse(html.replace("R=947", "R=948")), today).get(0).open,
        "URL must match session ID");
    rejects(
        () -> ClubClient.safe("https://evil.test/reservas/r.aspx?a=d&R=947", true),
        "cross-host action rejected");
    rejects(
        () -> ClubClient.safe("/reservas/r.aspx?a=d&R=947", false),
        "read cannot follow booking URL");
    rejects(
        () -> ClubClient.safe("http://cantolagua.sanguesa.es/reservas/r.aspx", true),
        "unencrypted action rejected");
    check(
        !ClubClient.direct(
            ClubClient.parse(Jsoup.parse(html.replace("R=947", "R=947&amp;a=d")), today).get(0)),
        "duplicate action parameters rejected");
    check(
        !ClubClient.created(new JSONObject("{\"code\":\"OK\",\"result\":{}}"), "947"),
        "generic OK is not confirmation");
    check(
        !ClubClient.created(
            new JSONObject(
                "{\"code\":\"OK\",\"result\":{\"asistenciasCreadas\":[{\"clase\":948}]}}"),
            "947"),
        "receipt for other session is not confirmation");
    check(
        ClubClient.created(
            new JSONObject(
                "{\"code\":\"OK\",\"result\":{\"asistenciasCreadas\":[{\"clase\":947}]}}"),
            "947"),
        "matching receipt confirms booking");
    check(
        !ClubClient.created(
            new JSONObject(
                "{\"code\":\"OK\",\"result\":{\"idlinea\":\"12\",\"asistenciasCreadas\":[{\"clase\":947}]}}"),
            "947"),
        "payment receipt is not confirmed");
    check(
        ClubClient.sessionDate("<b>viernes 1</b>/1", LocalDate.of(2026, 12, 30))
            .equals(LocalDate.of(2027, 1, 1)),
        "year rollover resolved with weekday");
    rejects(() -> ClubClient.sessionDate("lunes 1/1", today), "stale or mismatched date rejected");
    List<Plan.Slot> template = Collections.singletonList(slot);
    Map<String, List<Plan.Slot>> overrides = new HashMap<>();
    Instant now = Instant.parse("2026-10-15T04:00:00Z");
    List<Plan.Job> jobs = Plan.jobs(now, 4, template, overrides);
    check(
        jobs.get(0).release.equals(Instant.parse("2026-10-16T18:30:00Z")), "Madrid summer offset");
    check(
        jobs.get(2).release.equals(Instant.parse("2026-10-30T19:30:00Z")),
        "Madrid winter offset across DST");
    check(
        jobs.get(1).slot.key().equals(slot.key()), "weekly plan repeats without copying app state");
    overrides.put("2026-10-19", Collections.emptyList());
    check(
        Plan.jobs(now, 4, template, overrides).get(0).date.equals(LocalDate.of(2026, 10, 27)),
        "empty week override suppresses only that week");
    check(
        Plan.decode(Plan.encode(template)).get(0).key().equals(slot.key()),
        "plan persists losslessly");
    check(
        Plan.jobs(Instant.parse("2026-10-16T18:31:31Z"), 4, template, Collections.emptyMap())
            .get(0)
            .date
            .equals(LocalDate.of(2026, 10, 27)),
        "missed opening not booked late");
    check(
        Plan.jobs(now, 4, Arrays.asList(slot, slot), Collections.emptyMap()).size() == jobs.size(),
        "duplicate selected slots deduplicated");
    rejects(
        () -> ClubClient.signed(html.replace("class=\"identificado\"", "")),
        "unauthenticated catalogue rejected");
    int[] sends = {0};
    ClubClient client =
        new ClubClient(
            (url, fields, mutation) -> {
              sends[0]++;
              throw new java.io.IOException("timeout");
            });
    rejects(() -> client.reserve(item), "network failure surfaced");
    check(sends[0] == 1, "mutation never retried on timeout");
    List<String> urls = new ArrayList<>();
    ClubClient success =
        new ClubClient(
            (url, fields, mutation) -> {
              urls.add(url);
              return "{\"code\":\"OK\",\"result\":{\"asistenciasCreadas\":[{\"clase\":947}]}}";
            });
    check(
        success.reserve(item) && urls.get(0).contains("IdInscrito=99"),
        "selected person preserved in reservation");
    int[] calls = {0};
    ClubClient login =
        new ClubClient(
            (url, fields, mutation) -> {
              calls[0]++;
              if (url.contains("login.aspx") && fields == null)
                return "<form id='frmLogin'><input type='hidden' name='accion'"
                    + " value='login'></form>";
              if (fields != null) {
                check(
                    "u".equals(fields.get("mail")) && "p".equals(fields.get("clave")),
                    "login sends supplied fields");
                return "<body>Inicio</body>";
              }
              return html;
            });
    login.login("u", "p");
    check(calls[0] == 3, "login verifies protected catalogue instead of home marker");
    Map<String, List<Plan.Slot>> distant = new HashMap<>();
    distant.put("2027-01-04", template);
    check(
        Plan.jobs(now, 4, Collections.emptyList(), distant)
            .get(0)
            .date
            .equals(LocalDate.of(2027, 1, 5)),
        "distant isolated week still schedules an alarm");
    for (int day = 0; day < 5; day++) {
      Plan.Job example =
          new Plan.Job(
              new Plan.Slot(day, "8", "Global Training", "17:30"),
              LocalDate.of(2026, 10, 19).plusDays(day),
              4);
      check(
          example
                  .release
                  .atZone(Plan.ZONE)
                  .toLocalDate()
                  .equals(LocalDate.of(2026, 10, 15).plusDays(day))
              && example.release.atZone(Plan.ZONE).toLocalTime().equals(LocalTime.of(17, 30)),
          "confirmed four-day same-hour rule for " + Plan.DAYS[day]);
    }
    Plan.Job early =
        new Plan.Job(new Plan.Slot(0, "7", "Pilates", "17:30"), LocalDate.of(2026, 10, 19), 4);
    Plan.Job late =
        new Plan.Job(
            new Plan.Slot(0, "8", "Global Training", "20:30"), LocalDate.of(2026, 10, 19), 4);
    check(
        !early.release.equals(late.release),
        "different session hours have different release times");
    System.out.println("RESULT: " + count + " passed; 0 failed. No live bookings sent.");
  }
}
