package es.cantolagua.misemana;

import java.time.*;
import java.util.*;
import org.json.*;

/** Observed weekly timetable, independent of the user's selected bookings. */
public final class Timetable {
  public static final class Entry {
    public final Plan.Slot slot;
    public final LocalDate observed;

    public Entry(Plan.Slot slot, LocalDate observed) {
      this.slot = slot;
      this.observed = observed;
    }
  }

  private static String group(Plan.Slot slot) {
    return slot.day + "|" + slot.time;
  }

  public static List<Entry> decode(String json) throws JSONException {
    List<Entry> result = new ArrayList<>();
    JSONArray array = new JSONArray(json);
    for (int i = 0; i < array.length(); i++) {
      JSONObject o = array.getJSONObject(i);
      String date = o.optString("observedDate", "");
      result.add(new Entry(Plan.Slot.from(o), date.isEmpty() ? null : LocalDate.parse(date)));
    }
    return result;
  }

  public static String encode(List<Entry> entries) throws JSONException {
    JSONArray array = new JSONArray();
    for (Entry e : entries) {
      JSONObject o = e.slot.json();
      if (e.observed != null) o.put("observedDate", e.observed.toString());
      array.put(o);
    }
    return array.toString();
  }

  public static List<Entry> merge(List<Entry> previous, List<ClubClient.Session> sessions) {
    Map<String, List<Entry>> groups = new LinkedHashMap<>();
    for (Entry e : previous) groups.computeIfAbsent(group(e.slot), k -> new ArrayList<>()).add(e);
    Map<String, List<Entry>> incoming = new LinkedHashMap<>();
    for (ClubClient.Session s : sessions) {
      Plan.Slot slot =
          new Plan.Slot(s.date.getDayOfWeek().getValue() - 1, s.activity, s.name, s.time);
      String key = group(slot);
      List<Entry> entries = incoming.computeIfAbsent(key, k -> new ArrayList<>());
      if (!entries.isEmpty() && entries.get(0).observed.isAfter(s.date)) continue;
      if (!entries.isEmpty() && entries.get(0).observed.isBefore(s.date)) entries.clear();
      if (entries.stream().noneMatch(e -> e.slot.key().equals(slot.key())))
        entries.add(new Entry(slot, s.date));
    }
    for (Map.Entry<String, List<Entry>> incomingGroup : incoming.entrySet()) {
      List<Entry> old = groups.get(incomingGroup.getKey());
      LocalDate oldDate = null;
      if (old != null)
        for (Entry e : old)
          if (e.observed != null && (oldDate == null || e.observed.isAfter(oldDate)))
            oldDate = e.observed;
      if (oldDate == null || !oldDate.isAfter(incomingGroup.getValue().get(0).observed))
        groups.put(incomingGroup.getKey(), incomingGroup.getValue());
    }
    List<Entry> result = new ArrayList<>();
    for (List<Entry> entries : groups.values()) result.addAll(entries);
    result.sort(
        Comparator.comparingInt((Entry e) -> e.slot.day)
            .thenComparing(e -> e.slot.time)
            .thenComparing(e -> e.slot.name));
    return result;
  }

  public static List<Plan.Slot> choices(List<Entry> entries, int day, String time) {
    List<Plan.Slot> out = new ArrayList<>();
    for (Entry e : entries) if (e.slot.day == day && e.slot.time.equals(time)) out.add(e.slot);
    return out;
  }

  public static List<String> hours(List<Entry> entries, int day) {
    TreeSet<String> hours = new TreeSet<>();
    for (Entry e : entries) if (e.slot.day == day) hours.add(e.slot.time);
    // Offer the usual hours even when only part of a day has been published.
    // An observed morning clock takes priority: Tuesday's 09:20 is not Monday's 09:00.
    boolean morning = hours.stream().anyMatch(time -> time.compareTo("12:00") < 0);
    hours.addAll(Arrays.asList("16:30", "17:30", "18:30", "19:30", "20:30"));
    if (!morning) hours.addAll(Arrays.asList("09:00", "09:20"));
    return new ArrayList<>(hours);
  }

  public static String label(List<Entry> entries, int day, String time) {
    List<Plan.Slot> options = choices(entries, day, time);
    List<String> names = new ArrayList<>();
    for (Plan.Slot s : options) names.add(s.name);
    return time
        + " · "
        + (names.isEmpty() ? "Actividad pendiente de consultar" : String.join(" / ", names));
  }
}
