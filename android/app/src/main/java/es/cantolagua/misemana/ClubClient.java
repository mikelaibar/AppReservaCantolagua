package es.cantolagua.misemana;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Only this client knows the club's web protocol. Cookies never leave memory. */
public final class ClubClient {
  public static final String BASE = "https://cantolagua.sanguesa.es",
      PAGE = BASE + "/reservas/actividades.aspx";
  private final CookieManager cookies =
      new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  static final String USER_AGENT = "MiCantolagua/1.0.4";
  private final Transport transport;
  private final LocalDate testDate;

  interface Transport {
    String send(String url, Map<String, String> fields, boolean mutation) throws Exception;
  }

  public ClubClient() {
    this(null, null);
  }

  ClubClient(Transport transport) {
    this(transport, null);
  }

  ClubClient(Transport transport, LocalDate date) {
    this.transport = transport;
    this.testDate = date;
  }

  public static class Problem extends Exception {
    public Problem(String s) {
      super(s);
    }
  }

  public static final class Session {
    public String id, activity, name, time, href, person = "", room, status, detail;
    public LocalDate date;
    public boolean booked, waitlisted, open, paid;

    public String toString() {
      return Plan.DAYS[date.getDayOfWeek().getValue() - 1] + " " + time + " · " + name;
    }
  }

  private static final Pattern ACTIVITY = Pattern.compile("^Actividad([0-9]+)$");
  private static final Pattern DATE =
      Pattern.compile(
          "(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static String encode(String s) throws Exception {
    return URLEncoder.encode(s, "UTF-8");
  }

  static URI safe(String url, boolean action) throws Exception {
    URI u = new URI(PAGE).resolve(url);
    if (!"https".equals(u.getScheme())
        || !"cantolagua.sanguesa.es".equals(u.getHost())
        || u.getPort() != -1
        || u.getUserInfo() != null
        || u.getFragment() != null) throw new Problem("Enlace inesperado de la web");
    if (action && !"/reservas/r.aspx".equals(u.getPath()))
      throw new Problem("Acción de reserva desconocida");
    if (!action && "/reservas/r.aspx".equals(u.getPath()))
      throw new Problem("No se puede consultar un enlace que modifica reservas");
    return u;
  }

  private String request(String url, Map<String, String> fields, boolean mutation)
      throws Exception {
    URI uri = safe(url, mutation);
    if (transport != null) return transport.send(uri.toString(), fields, mutation);
    for (int redirects = 0; redirects < 5; redirects++) {
      HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
      c.setConnectTimeout(12000);
      c.setReadTimeout(12000);
      c.setInstanceFollowRedirects(false);
      c.setRequestProperty("User-Agent", USER_AGENT);
      c.setRequestProperty("Accept", mutation ? "application/json" : "text/html,application/json");
      c.setRequestProperty("Referer", PAGE);
      c.setRequestProperty("Cache-Control", "no-cache");
      for (Map.Entry<String, List<String>> h : cookies.get(uri, Collections.emptyMap()).entrySet())
        c.setRequestProperty(h.getKey(), String.join("; ", h.getValue()));
      try {
        if (fields != null) {
          StringBuilder body = new StringBuilder();
          for (Map.Entry<String, String> f : fields.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(encode(f.getKey())).append('=').append(encode(f.getValue()));
          }
          byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
          c.setRequestMethod("POST");
          c.setDoOutput(true);
          c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
          c.setFixedLengthStreamingMode(b.length);
          try (OutputStream os = c.getOutputStream()) {
            os.write(b);
          }
        }
        int code = c.getResponseCode();
        cookies.put(uri, c.getHeaderFields());
        if (code >= 300 && code < 400) {
          if (mutation)
            throw new Problem(
                "Respuesta incierta. Comprueba la reserva en la web antes de volver a intentarlo");
          String next = c.getHeaderField("Location");
          if (next == null) throw new Problem("Redirección sin destino");
          uri = safe(uri.resolve(next).toString(), false);
          if (code == 303 || code == 301 || code == 302) fields = null;
          continue;
        }
        if (code != 200) throw new Problem("La web ha respondido con un error (" + code + ")");
        try (InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
          byte[] buf = new byte[8192];
          int n;
          while ((n = in.read(buf)) != -1) {
            if (out.size() + n > 4 * 1024 * 1024) throw new Problem("Respuesta demasiado grande");
            out.write(buf, 0, n);
          }
          return out.toString("UTF-8");
        }
      } finally {
        c.disconnect();
      }
    }
    throw new Problem("Demasiadas redirecciones");
  }

  public void login(String user, String password) throws Exception {
    Document d = Jsoup.parse(request(BASE + "/registro/login.aspx?pagina=%2f", null, false));
    Element form = d.selectFirst("form#frmLogin");
    if (form == null) throw new Problem("Ha cambiado la página de acceso");
    Map<String, String> fields = new LinkedHashMap<>();
    for (Element e : form.select("input[type=hidden][name]"))
      fields.put(e.attr("name"), e.attr("value"));
    fields.put("mail", user.trim());
    fields.put("clave", password);
    fields.put("accion", "login");
    fields.put("pagina", "/reservas/actividades.aspx");
    fields.put("js", "");
    request(BASE + "/registro/login.aspx", fields, false);
    signed(request(PAGE, null, false));
  }

  static Document signed(String html) throws Problem {
    Document d = Jsoup.parse(html, PAGE);
    if (d.selectFirst("body.identificado") == null || d.selectFirst("#frmLogin") != null)
      throw new Problem("No se ha podido iniciar sesión. Comprueba usuario y contraseña");
    return d;
  }

  public List<Session> catalogue() throws Exception {
    return calendar(true);
  }

  public List<Session> publicCatalogue() throws Exception {
    return calendar(false);
  }

  private Document calendarDocument(String html, boolean authenticated) throws Problem {
    if (authenticated) return signed(html);
    Document document = Jsoup.parse(html, PAGE);
    if (document.selectFirst("#frmLogin") != null)
      throw new Problem("Cantolagua pide iniciar sesión para consultar este calendario");
    return document;
  }

  private List<Session> calendar(boolean authenticated) throws Exception {
    LocalDate today = testDate != null ? testDate : LocalDate.now(Plan.ZONE);
    Document document = calendarDocument(request(PAGE, null, false), authenticated);
    LinkedHashMap<String, Session> sessions = new LinkedHashMap<>();
    addSessions(sessions, parse(document, today));
    // Some mobile responses contain just one day's cards and link to the other tabs.
    // Follow only the calendar's own date links; booking/cancellation links are never read.
    LinkedHashMap<String, LocalDate> tabs = new LinkedHashMap<>();
    for (Element link : document.select(".Clases_EncabezadoDia .Clases_Dia a[href]")) {
      URI uri = safe(link.attr("href"), false);
      Map<String, String> parameters = query(uri);
      if (!uri.getPath().equals("/reservas/actividades.aspx")
          || parameters.size() != 1
          || !parameters.containsKey("dia"))
        throw new Problem("Enlace de día inesperado en el calendario");
      LocalDate date = LocalDate.parse(parameters.get("dia"));
      if (Math.abs(ChronoUnit.DAYS.between(today, date)) > 45)
        throw new Problem("El calendario enlaza una fecha fuera de rango");
      tabs.put(uri.toString(), date);
    }
    if (tabs.size() > 14)
      throw new Problem("El calendario contiene demasiados días para una consulta");
    for (Map.Entry<String, LocalDate> tab : tabs.entrySet()) {
      if (sessions.values().stream().anyMatch(session -> session.date.equals(tab.getValue())))
        continue;
      List<Session> day =
          parse(calendarDocument(request(tab.getKey(), null, false), authenticated), today);
      if (!day.isEmpty() && day.stream().noneMatch(session -> session.date.equals(tab.getValue())))
        throw new Problem("Cantolagua no ha devuelto el día solicitado");
      addSessions(sessions, day);
    }
    return new ArrayList<>(sessions.values());
  }

  private static void addSessions(Map<String, Session> destination, List<Session> incoming)
      throws Problem {
    for (Session session : incoming) {
      Session previous = destination.get(session.id);
      if (previous != null
          && (!previous.date.equals(session.date)
              || !previous.activity.equals(session.activity)
              || !previous.time.equals(session.time)))
        throw new Problem("Identificador de sesión incoherente en el calendario");
      destination.put(session.id, session);
    }
  }

  public static LocalDate sessionDate(String raw, LocalDate today) throws Problem {
    Matcher m = DATE.matcher(Jsoup.parse(raw).text());
    if (!m.find()) throw new Problem("Fecha de actividad desconocida");
    String day = m.group(1).toLowerCase(Locale.ROOT).replace('é', 'e').replace('á', 'a');
    int wd =
        Arrays.asList("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
                .indexOf(day)
            + 1;
    LocalDate best = null;
    long distance = Long.MAX_VALUE;
    for (int y = today.getYear() - 1; y <= today.getYear() + 1; y++)
      try {
        LocalDate d = LocalDate.of(y, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)));
        long delta = Math.abs(ChronoUnit.DAYS.between(today, d));
        if (d.getDayOfWeek().getValue() == wd && delta < distance) {
          best = d;
          distance = delta;
        }
      } catch (DateTimeException ignored) {
      }
    if (best == null || distance > 45)
      throw new Problem("No se puede verificar el año de la actividad");
    return best;
  }

  static List<Session> parse(Document d, LocalDate today) throws Exception {
    if (d.selectFirst("#cuadricula") == null)
      throw new Problem("Ha cambiado el calendario de la web");
    String person = "";
    Element select = d.selectFirst("select#IdInscrito");
    if (select != null) {
      Element selected = select.selectFirst("option[selected]");
      if (selected == null) selected = select.selectFirst("option");
      if (selected != null) {
        person = selected.attr("value");
        if (!person.matches("[0-9]+")) throw new Problem("Selecciona tu persona en la web");
      }
    }
    List<Session> out = new ArrayList<>();
    for (Element e : d.select(".Clases_Clase")) {
      if (!e.id().matches("Clase_[0-9]+")) continue;
      Element a = e.selectFirst(".Clase_Actividad a"), clock = e.selectFirst(".hinicio_clase");
      if (a == null || clock == null) continue;
      Session s = new Session();
      s.id = e.id().substring(6);
      for (String cls : e.classNames()) {
        Matcher m = ACTIVITY.matcher(cls);
        if (m.matches()) {
          if (s.activity != null) throw new Problem("Identificador de actividad ambiguo");
          s.activity = m.group(1);
        }
      }
      if (s.activity == null) continue;
      s.name = a.text();
      s.href = a.attr("href");
      s.time = LocalTime.parse(clock.text().trim().replaceFirst("^(\\d):", "0$1:")).toString();
      s.date = sessionDate(e.attr("data-fecha"), today);
      s.person = person;
      s.room = e.select(".Clase_Sala").text();
      boolean pendingPayment = e.hasClass("reserva__sinpagar")
          || !e.attr("data-idlinea").trim().isEmpty();
      s.booked = e.hasClass("Clase__Inscrito") && !pendingPayment;
      s.waitlisted = e.hasClass("Clase__EnListaEspera");
      String amount = e.attr("data-importe").trim();
      int price = priceState(amount);
      s.paid = pendingPayment || price > 0;
      boolean full = e.hasClass("Clase__ListaEspera") || s.waitlisted || e.hasClass("Clase__Llena");
      boolean future = e.hasClass("Clase__Futura") || e.hasClass("Clase__Pasada");
      s.open =
          !s.booked && !full && !future && !s.paid && price == 0
              && e.hasClass("Clase__Clicable") && direct(s);
      s.status =
          s.booked
              ? "Ya reservada"
              : s.waitlisted
                  ? "En lista de espera"
                  : s.paid
                      ? "Requiere pago"
                      : price < 0
                          ? "Importe no reconocido"
                          : full
                          ? "Completa"
                          : future ? "Aún no disponible" : s.open ? "Disponible" : "No disponible";
      s.detail = pendingPayment
          ? "Cantolagua muestra una inscripción pendiente de pago. Compruébala en la web"
          : price != 0
              ? "Importe indicado en el calendario: "
                  + amount.replaceAll("[\\p{Cntrl}]", " ").substring(0, Math.min(amount.length(), 80))
                  + ". No se ha enviado una reserva"
              : "Estado consultado en Cantolagua. No se ha enviado una reserva";
      out.add(s);
    }
    return out;
  }

  // Calendar amounts are display strings (for example "0,00 €"), not raw numbers.
  // Unknown formats remain blocked without claiming that a payment is required.
  private static int priceState(String raw) {
    String amount = raw.replace('\u00a0', ' ').replace('\u202f', ' ').trim();
    if (amount.isEmpty()) return 0;
    amount = amount.replaceFirst("^(?:€|EUR)\\s*", "")
        .replaceFirst("\\s*(?:€|EUR)$", "").trim();
    if (!amount.matches("[0-9]+(?:[.,][0-9]{1,2})?")) return -1;
    return new BigDecimal(amount.replace(',', '.')).signum();
  }

  static Map<String, String> query(URI u) throws Exception {
    Map<String, String> out = new HashMap<>();
    if (u.getRawQuery() != null)
      for (String pair : u.getRawQuery().split("&")) {
        String[] p = pair.split("=", 2);
        String key = URLDecoder.decode(p[0], "UTF-8");
        if (out.containsKey(key)) throw new Problem("Parámetro de reserva duplicado");
        out.put(key, p.length > 1 ? URLDecoder.decode(p[1], "UTF-8") : "");
      }
    return out;
  }

  static boolean direct(Session s) {
    try {
      Map<String, String> q = query(safe(s.href, true));
      return "d".equals(q.get("a")) && s.id.equals(q.get("R"));
    } catch (Exception e) {
      return false;
    }
  }

  public static Session match(List<Session> sessions, Plan.Job job) throws Problem {
    Session found = null;
    for (Session s : sessions)
      if (s.date.equals(job.date)
          && s.time.equals(job.slot.time)
          && s.activity.equals(job.slot.activity)) {
        if (found != null)
          throw new Problem("Hay dos sesiones iguales. Selecciónala manualmente en la web");
        found = s;
      }
    return found;
  }

  static boolean created(JSONObject o, String id) {
    JSONObject r = o.optJSONObject("result");
    if (!"OK".equals(o.optString("code"))
        || r == null
        || !r.optString("idlinea").isEmpty()
        || !r.optString("errores").isEmpty()) return false;
    JSONArray a = r.optJSONArray("asistenciasCreadas");
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject item = a.optJSONObject(i);
        if (item != null && id.equals(item.optString("clase"))) return true;
      }
    return false;
  }

  public boolean reserve(Session s) throws Exception {
    if (!s.open || s.paid || !direct(s))
      throw new Problem("Esta sesión no admite una reserva directa");
    URI u = safe(s.href, true);
    Map<String, String> q = query(u);
    if (!s.person.isEmpty()) {
      if (q.containsKey("IdInscrito") && !s.person.equals(q.get("IdInscrito")))
        throw new Problem("Persona de reserva inesperada");
      q.put("IdInscrito", s.person);
    }
    q.put("js", "1");
    q.put("json", "on");
    q.put("rndm", LocalTime.now(Plan.ZONE).toString());
    StringBuilder url = new StringBuilder(BASE + u.getPath() + "?");
    for (Map.Entry<String, String> f : q.entrySet())
      url.append(encode(f.getKey())).append('=').append(encode(f.getValue())).append('&');
    return created(new JSONObject(request(url.toString(), null, true)), s.id);
  }
}
