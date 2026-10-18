package es.cantolagua.misemana;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.jsoup.Jsoup;

public final class TimetableTests {
  static int count;

  static void check(boolean good, String label) {
    if (!good) throw new AssertionError(label);
    count++;
    System.out.println("PASS " + label);
  }

  static ClubClient.Session session(LocalDate date, String time, String activity, String name) {
    ClubClient.Session s = new ClubClient.Session();
    s.date = date;
    s.time = time;
    s.activity = activity;
    s.name = name;
    return s;
  }

  public static void main(String[] args) throws Exception {
    String html = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
    List<ClubClient.Session> sessions =
        ClubClient.parse(Jsoup.parse(html), LocalDate.of(2026, 10, 18));
    List<Timetable.Entry> entries = Timetable.merge(Collections.emptyList(), sessions);
    check(entries.size() == 26, "all sessions in the real four-day calendar retained");
    check(
        Timetable.label(entries, 0, "09:00").equals("09:00 · Pilates"),
        "Monday morning is Pilates at 09:00");
    check(
        Timetable.label(entries, 1, "09:20").equals("09:20 · Step-Training"),
        "reported regression: Tuesday is Step-Training at 09:20");
    check(
        Timetable.choices(entries, 1, "09:00").isEmpty(),
        "Monday Pilates never copied to Tuesday morning");
    check(
        Timetable.label(entries, 2, "09:00").equals("09:00 · Movilidad"),
        "Wednesday morning remains Movilidad");
    check(
        Timetable.label(entries, 3, "09:20").equals("09:20 · Step-Training"),
        "Thursday morning remains Step-Training");
    check(
        Timetable.hours(entries, 1).get(0).equals("09:20"),
        "Tuesday uses the class clock instead of the table's 09:00 row");
    check(
        Timetable.choices(entries, 0, "17:30").size() == 2,
        "parallel activities at one time both retained");
    check(
        Timetable.label(entries, 1, "20:30").equals("20:30 · Global Training"),
        "Tuesday evening remains assigned to Tuesday");
    List<Timetable.Entry> restored = Timetable.decode(Timetable.encode(entries));
    check(
        Timetable.label(restored, 1, "09:20").equals("09:20 · Step-Training"),
        "day, time and activity survive persistence");
    List<ClubClient.Session> mondayOnly =
        Collections.singletonList(session(LocalDate.of(2026, 10, 26), "09:00", "7", "Pilates"));
    List<Timetable.Entry> partial = Timetable.merge(restored, mondayOnly);
    check(
        Timetable.label(partial, 1, "09:20").equals("09:20 · Step-Training")
            && partial.size() == 26,
        "a new week publishing only Monday preserves other weekdays");
    List<Timetable.Entry> friday =
        Timetable.merge(
            partial,
            Collections.singletonList(
                session(LocalDate.of(2026, 10, 23), "18:30", "12", "Total Fuerza")));
    check(
        Timetable.label(Timetable.merge(friday, mondayOnly), 4, "18:30")
            .equals("18:30 · Total Fuerza"),
        "Friday reuses its own observed activity in following weeks");
    List<Timetable.Entry> changed =
        Timetable.merge(
            friday,
            Collections.singletonList(
                session(LocalDate.of(2026, 10, 27), "09:20", "11", "Movilidad")));
    check(
        Timetable.label(changed, 1, "09:20").equals("09:20 · Movilidad")
            && Timetable.choices(changed, 1, "09:20").size() == 1,
        "a newer observed class replaces that weekday/time only");
    check(
        Timetable.label(Timetable.merge(changed, sessions), 1, "09:20").equals("09:20 · Movilidad"),
        "older calendar cannot overwrite a newer observation");
    check(
        Timetable.choices(entries, 4, "09:00").isEmpty()
            && Timetable.label(entries, 4, "09:00")
                .equals("09:00 · Actividad pendiente de consultar"),
        "unpublished Friday never inherits Monday's activity");
    check(
        Timetable.hours(entries, 4).contains("17:30")
            && Timetable.hours(Collections.emptyList(), 1).contains("09:20"),
        "hours are offered even before an activity has been saved");
    List<Timetable.Entry> legacy =
        Timetable.decode(
            Plan.encode(
                Collections.singletonList(new Plan.Slot(1, "14", "Step-Training", "09:20"))));
    check(
        Timetable.label(legacy, 1, "09:20").equals("09:20 · Step-Training"),
        "version 1.0.0 catalogue migrates without losing times");
    for (int day = 0; day < 5; day++)
      for (String time : Timetable.hours(entries, day))
        if (!Timetable.label(entries, day, time).startsWith(time + " · "))
          throw new AssertionError("missing hour");
    check(true, "every selectable option includes its hour");
    check(
        Timetable.hours(friday, 4).contains("17:30")
            && Timetable.choices(friday, 4, "17:30").isEmpty(),
        "partially published weekday still offers the usual hours without an invented activity");
    System.out.println("RESULT: " + count + " timetable tests passed; no web requests.");
  }
}
