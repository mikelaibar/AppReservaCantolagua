package es.cantolagua.misemana;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.jsoup.Jsoup;

/** Parses an explicitly downloaded public calendar. Does not authenticate or book. */
public final class PublicCalendarCheck {
  public static void main(String[] args) throws Exception {
    String html = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
    List<ClubClient.Session> sessions =
        ClubClient.parse(Jsoup.parse(html), LocalDate.now(Plan.ZONE));
    if (sessions.isEmpty()) throw new AssertionError("No sessions parsed");
    Set<String> types = new HashSet<>();
    for (ClubClient.Session s : sessions) types.add(s.activity);
    System.out.println(
        "PUBLIC CALENDAR: "
            + sessions.size()
            + " sessions, "
            + types.size()
            + " activity types parsed. No authentication or bookings.");
  }
}
