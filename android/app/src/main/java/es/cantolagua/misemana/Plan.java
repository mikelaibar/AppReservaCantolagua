package es.cantolagua.misemana;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Calendar rules are always interpreted in the club's timezone. */
public final class Plan {
  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  public static final String[] DAYS = {
    "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
  };

  public static final class Slot {
    public final int day;
    public final String activity, name, time;

    public Slot(int day, String activity, String name, String time) {
      if (day < 0 || day > 6 || !activity.matches("[0-9]+"))
        throw new IllegalArgumentException("Actividad no válida");
      this.day = day;
      this.activity = activity;
      this.name = name;
      this.time = LocalTime.parse(time).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public JSONObject json() throws JSONException {
      return new JSONObject()
          .put("day", day)
          .put("activity", activity)
          .put("name", name)
          .put("time", time);
    }

    public static Slot from(JSONObject o) throws JSONException {
      return new Slot(
          o.getInt("day"), o.getString("activity"), o.getString("name"), o.getString("time"));
    }

    public String key() {
      return day + "|" + activity + "|" + time;
    }

    public String toString() {
      return time + " · " + name;
    }
  }

  public static final class Job {
    public final Slot slot;
    public final LocalDate date;
    public final Instant release;

    public Job(Slot s, LocalDate d, int lead, LocalTime opening) {
      slot = s;
      date = d;
      release = d.minusDays(lead).atTime(opening).atZone(ZONE).toInstant();
    }

    public String key() {
      return date + "|" + slot.activity + "|" + slot.time;
    }

    public String label() {
      return slot.name
          + " · "
          + date.format(DateTimeFormatter.ofPattern("dd/MM"))
          + " "
          + slot.time;
    }
  }

  public static LocalDate monday(LocalDate date) {
    return date.minusDays(date.getDayOfWeek().getValue() - 1);
  }

  public static List<Slot> decode(String text) {
    List<Slot> out = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(text);
      for (int i = 0; i < a.length(); i++) out.add(Slot.from(a.getJSONObject(i)));
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer el horario guardado", e);
    }
    return out;
  }

  public static String encode(List<Slot> slots) {
    JSONArray a = new JSONArray();
    try {
      for (Slot s : slots) a.put(s.json());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    return a.toString();
  }

  public static List<Job> jobs(
      Instant now,
      int lead,
      LocalTime opening,
      List<Slot> template,
      Map<String, List<Slot>> overrides) {
    if (lead < 0 || lead > 14) throw new IllegalArgumentException("Antelación fuera de rango");
    List<Job> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    LocalDate today = now.atZone(ZONE).toLocalDate();
    Set<LocalDate> dates = new TreeSet<>();
    for (int n = 0; n < 35; n++) dates.add(today.plusDays(n));
    // An isolated exception beyond the rolling window still needs its own alarm.
    for (String key : overrides.keySet()) {
      LocalDate week = LocalDate.parse(key);
      for (int day = 0; day < 7; day++) {
        LocalDate date = week.plusDays(day);
        if (!date.isBefore(today)) dates.add(date);
      }
    }
    for (LocalDate d : dates) {
      List<Slot> slots = overrides.getOrDefault(monday(d).toString(), template);
      for (Slot s : slots)
        if (s.day == d.getDayOfWeek().getValue() - 1) {
          Job j = new Job(s, d, lead, opening);
          if (d.atTime(LocalTime.parse(s.time)).atZone(ZONE).toInstant().isAfter(now)
              && !j.release.isBefore(now.minusSeconds(90))
              && seen.add(j.key())) out.add(j);
        }
    }
    out.sort(Comparator.comparing((Job j) -> j.release).thenComparing(j -> j.slot.time));
    return out;
  }
}
