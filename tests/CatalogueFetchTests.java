package es.cantolagua.misemana;

import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class CatalogueFetchTests {
  static int passed;

  static void check(boolean good, String message) {
    if (!good) throw new AssertionError(message);
    passed++;
    System.out.println("PASS " + message);
  }

  interface Action {
    void run() throws Exception;
  }

  static void rejects(Action action, String message) throws Exception {
    boolean rejected = false;
    try {
      action.run();
    } catch (Exception e) {
      rejected = true;
    }
    check(rejected, message);
  }

  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args[0]);
    LocalDate now = LocalDate.of(2026, 10, 18);
    String desktop = new String(Files.readAllBytes(root.resolve("calendar-week.html")), "UTF-8");
    Map<String, String> days = new HashMap<>();
    for (int day = 19; day <= 22; day++)
      days.put(
          "2026-10-" + day,
          new String(
              Files.readAllBytes(root.resolve("calendar-mobile-" + day + ".html")), "UTF-8"));
    List<String> reads = new ArrayList<>();
    ClubClient mobile =
        new ClubClient(
            (url, fields, mutation) -> {
              check(!mutation && fields == null, "mobile day request is read-only");
              reads.add(url);
              if (url.equals(ClubClient.PAGE)) return days.get("2026-10-19");
              return days.get(ClubClient.query(new java.net.URI(url)).get("dia"));
            },
            now);
    List<ClubClient.Session> sessions = mobile.publicCatalogue();
    check(
        sessions.size() == 26,
        "actual mobile responses produce all 26 sessions instead of only seven on Monday");
    check(reads.size() == 4, "selected Monday is not fetched a second time");
    check(
        sessions.stream().map(s -> s.date).distinct().count() == 4,
        "mobile day tabs cover Monday through Thursday");
    check(
        sessions.stream()
            .anyMatch(
                s ->
                    s.name.equals("Step-Training")
                        && s.time.equals("09:20")
                        && s.date.getDayOfWeek() == DayOfWeek.TUESDAY),
        "mobile Tuesday includes Step-Training at 09:20");
    int[] desktopReads = {0};
    ClubClient full =
        new ClubClient(
            (url, fields, mutation) -> {
              desktopReads[0]++;
              return desktop;
            },
            now);
    check(
        full.publicCatalogue().size() == 26 && desktopReads[0] == 1,
        "full calendar needs only one read");
    check(
        !ClubClient.USER_AGENT.contains("Android"),
        "request identity avoids the site's one-day Android view");
    ClubClient signed =
        new ClubClient(
            (url, fields, mutation) -> {
              String page =
                  url.equals(ClubClient.PAGE)
                      ? days.get("2026-10-19")
                      : days.get(ClubClient.query(new java.net.URI(url)).get("dia"));
              return page.replace("<body>", "<body class='identificado'>");
            },
            now);
    check(
        signed.catalogue().size() == 26,
        "authenticated booking lookup also reads all published days");
    ClubClient expired =
        new ClubClient(
            (url, fields, mutation) ->
                url.equals(ClubClient.PAGE)
                    ? days.get("2026-10-19").replace("<body>", "<body class='identificado'>")
                    : "<body><form id='frmLogin'></form></body>",
            now);
    rejects(
        expired::catalogue, "expired login on another day is not accepted as a complete catalogue");
    String malicious =
        days.get("2026-10-19")
            .replace(
                "/reservas/actividades.aspx?dia=2026-10-20",
                "/reservas/r.aspx?a=borrardeclase&amp;R=640");
    int[] calls = {0};
    ClubClient guarded =
        new ClubClient(
            (url, fields, mutation) -> {
              calls[0]++;
              return malicious;
            },
            now);
    rejects(
        guarded::publicCatalogue, "calendar never follows a cancellation URL disguised as a tab");
    check(calls[0] == 1, "unsafe tab is rejected before a network request");
    ClubClient ignored = new ClubClient((url, fields, mutation) -> days.get("2026-10-19"), now);
    rejects(
        ignored::publicCatalogue,
        "server ignoring the requested day is reported instead of silently retaining Monday only");
    ClubClient unavailable =
        new ClubClient(
            (url, fields, mutation) -> {
              throw new java.net.SocketTimeoutException("Synthetic timeout");
            },
            now);
    rejects(
        unavailable::publicCatalogue,
        "a failed calendar download is an error, not an empty successful timetable");
    System.out.println("RESULT: " + passed + " catalogue fetch checks passed; no web requests.");
  }
}
