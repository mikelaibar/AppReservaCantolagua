package es.cantolagua.misemana;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  private static final int GREEN = 0xff195947,
      INK = 0xff183b32,
      MUTED = 0xff5c7068,
      BG = 0xfff5f7f1,
      LINE = 0xffdce5dc;
  private Store store;
  private LinearLayout root, content;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private int tab = 0;
  private boolean recurring = true, busy = false;
  private LocalDate week = Plan.monday(LocalDate.now(Plan.ZONE));

  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    store = new Store(this);
    if (state != null) {
      tab = state.getInt("tab");
      recurring = state.getBoolean("recurring", true);
      week = LocalDate.parse(state.getString("week", week.toString()));
    }
    render();
  }

  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putInt("tab", tab);
    b.putBoolean("recurring", recurring);
    b.putString("week", week.toString());
  }

  protected void onResume() {
    super.onResume();
    if (store != null) {
      try {
        Scheduler.refresh(this);
      } catch (Exception e) {
        toast("Revisa tu configuración");
      }
      render();
    }
  }

  public void onDestroy() {
    worker.shutdown();
    super.onDestroy();
  }

  private int dp(int x) {
    return Math.round(x * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private TextView text(String value, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    t.setPadding(0, dp(5), 0, dp(5));
    return t;
  }

  private Button button(String label, Runnable action, boolean primary) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(16);
    b.setAllCaps(false);
    b.setTextColor(primary ? Color.WHITE : GREEN);
    b.setBackground(shape(primary ? GREEN : 0xffe9efe4, 14));
    b.setMinHeight(dp(50));
    b.setPadding(dp(14), dp(6), dp(14), dp(6));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(6), 0, dp(6));
    b.setLayoutParams(p);
    b.setEnabled(!busy);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private LinearLayout card() {
    LinearLayout l = column();
    l.setPadding(dp(18), dp(14), dp(18), dp(14));
    l.setBackground(shape(Color.WHITE, 20));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(8), 0, dp(8));
    l.setLayoutParams(p);
    content.addView(l);
    return l;
  }

  private void render() {
    root = column();
    root.setBackgroundColor(BG);
    root.setPadding(dp(20), dp(12), dp(20), dp(8));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          root.setPadding(
              dp(20) + insets.getSystemWindowInsetLeft(),
              dp(12) + insets.getSystemWindowInsetTop(),
              dp(20) + insets.getSystemWindowInsetRight(),
              dp(8) + insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    root.requestApplyInsets();
    root.addView(text("CANTOLAGUA", 12, GREEN, true));
    root.addView(text("Tu semana, más fácil", 27, INK, true));
    LinearLayout nav = new LinearLayout(this);
    String[] names = {"Mi semana", "Reservas", "Ajustes"};
    for (int i = 0; i < 3; i++) {
      final int n = i;
      Button b =
          button(
              names[i],
              () -> {
                tab = n;
                render();
              },
              tab == i);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
      p.setMargins(dp(2), dp(8), dp(2), dp(8));
      nav.addView(b, p);
    }
    root.addView(nav);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = column();
    content.setPadding(0, 0, 0, dp(18));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (tab == 0) showWeek();
    else if (tab == 1) showHistory();
    else showSettings();
  }

  private void showWeek() {
    LinearLayout status = card();
    status.addView(
        text(
            store.enabled() ? "● Reservas automáticas activas" : "Reservas automáticas en pausa",
            19,
            GREEN,
            true));
    status.addView(text(store.status(), 15, MUTED, false));
    Switch enabled = new Switch(this);
    enabled.setText("Reservar por mí");
    enabled.setTextSize(18);
    enabled.setTextColor(INK);
    enabled.setMinHeight(dp(52));
    enabled.setChecked(store.enabled());
    enabled.setEnabled(!busy);
    status.addView(enabled);
    enabled.setOnCheckedChangeListener(
        (b, on) -> {
          if (on) {
            if (!store.verified() || !store.hasCredentials()) {
              toast("Primero conecta tu cuenta en Ajustes");
              tab = 2;
              render();
              return;
            }
            if (store.template().isEmpty() && store.overrides().isEmpty()) {
              toast("Añade al menos una actividad");
              render();
              return;
            }
            if (!Scheduler.allowed(this)) {
              toast("Permite Alarmas y recordatorios y vuelve a activar las reservas");
              open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true);
              render();
              return;
            }
            if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
              requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 5);
          }
          store.enabled(on);
          Scheduler.refresh(this);
          render();
        });
    LinearLayout mode = card();
    mode.addView(text("Elige tu horario", 22, INK, true));
    mode.addView(
        text(
            "El horario habitual se repite cada semana. Puedes hacer cambios para una semana"
                + " concreta.",
            15,
            MUTED,
            false));
    mode.addView(
        button(
            recurring ? "✓ Todas las semanas" : "Todas las semanas",
            () -> {
              recurring = true;
              render();
            },
            recurring));
    mode.addView(
        button(
            !recurring ? "✓ Solo una semana" : "Solo una semana",
            () -> {
              recurring = false;
              render();
            },
            !recurring));
    if (!recurring) {
      LinearLayout row = new LinearLayout(this);
      Button
          prev =
              button(
                  "‹",
                  () -> {
                    week = week.minusWeeks(1);
                    render();
                  },
                  false),
          next =
              button(
                  "›",
                  () -> {
                    week = week.plusWeeks(1);
                    render();
                  },
                  false);
      prev.setContentDescription("Semana anterior");
      next.setContentDescription("Semana siguiente");
      row.addView(prev, new LinearLayout.LayoutParams(dp(52), dp(48)));
      TextView range =
          text(
              week.format(DateTimeFormatter.ofPattern("dd MMM", new Locale("es")))
                  + " – "
                  + week.plusDays(6)
                      .format(DateTimeFormatter.ofPattern("dd MMM", new Locale("es"))),
              16,
              INK,
              true);
      range.setGravity(Gravity.CENTER);
      row.addView(range, new LinearLayout.LayoutParams(0, dp(48), 1));
      row.addView(next, new LinearLayout.LayoutParams(dp(52), dp(48)));
      mode.addView(row);
      mode.addView(
          text(
              store.overrides().containsKey(week.toString())
                  ? "Esta semana tiene cambios propios"
                  : "Esta semana usa tu horario habitual",
              14,
              MUTED,
              false));
      mode.addView(
          button(
              "Volver al horario habitual",
              () ->
                  confirm(
                      "Restablecer semana",
                      "Se quitarán los cambios de esta semana.",
                      () -> {
                        try {
                          store.resetWeek(week);
                          changed();
                        } catch (Exception e) {
                          toast("No se pudo guardar");
                        }
                      }),
              false));
    }
    List<Plan.Slot> slots = recurring ? store.template() : store.week(week);
    for (int d = 0; d < 7; d++) {
      final int day = d;
      List<Plan.Slot> selected = new ArrayList<>();
      for (Plan.Slot s : slots) if (s.day == d) selected.add(s);
      if (d >= 5 && selected.isEmpty()) continue;
      selected.sort(Comparator.comparing(s -> s.time));
      LinearLayout c = card();
      c.addView(
          text(
              Plan.DAYS[d]
                  + (!recurring
                      ? " · " + week.plusDays(d).format(DateTimeFormatter.ofPattern("dd/MM"))
                      : ""),
              20,
              INK,
              true));
      if (selected.isEmpty()) c.addView(text("Sin actividades elegidas", 15, MUTED, false));
      for (Plan.Slot s : selected) {
        c.addView(text(s.time + "  " + s.name, 18, INK, true));
        c.addView(
            button(
                "Quitar " + s.name,
                () -> {
                  List<Plan.Slot> updated =
                      new ArrayList<>(recurring ? store.template() : store.week(week));
                  updated.removeIf(x -> x.key().equals(s.key()));
                  persist(updated);
                },
                false));
      }
      c.addView(button("+ Añadir actividad", () -> chooseSlot(day), false));
    }
    content.addView(
        text(
            "Apertura configurada: "
                + store.lead()
                + " días naturales antes, a la misma hora de cada clase (Madrid).",
            14,
            MUTED,
            false));
  }

  private void chooseSlot(int day) {
    List<Plan.Slot> choices = new ArrayList<>();
    for (Plan.Slot s : store.catalogue()) if (s.day == day) choices.add(s);
    choices.sort(Comparator.comparing(s -> s.time));
    List<String> labels = new ArrayList<>();
    for (Plan.Slot s : choices) labels.add(s.toString());
    labels.add("Elegir otra hora…");
    new AlertDialog.Builder(this)
        .setTitle(Plan.DAYS[day] + ": elige una clase")
        .setItems(
            labels.toArray(new String[0]),
            (d, n) -> {
              if (n == choices.size()) customSlot(day);
              else addSlot(choices.get(n));
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private void customSlot(int day) {
    LinkedHashMap<String, Plan.Slot> types = new LinkedHashMap<>();
    for (Plan.Slot s : store.catalogue()) types.put(s.activity, s);
    if (types.isEmpty()) {
      toast("Conecta tu cuenta en Ajustes para cargar las actividades");
      tab = 2;
      render();
      return;
    }
    List<Plan.Slot> options = new ArrayList<>(types.values());
    String[] labels = new String[options.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = options.get(i).name;
    new AlertDialog.Builder(this)
        .setTitle("Actividad")
        .setItems(
            labels,
            (d, n) -> {
              Plan.Slot type = options.get(n);
              new TimePickerDialog(
                      this,
                      (p, h, m) -> {
                        Plan.Slot s =
                            new Plan.Slot(
                                day,
                                type.activity,
                                type.name,
                                String.format(Locale.ROOT, "%02d:%02d", h, m));
                        confirm(
                            "Confirmar horario",
                            s
                                + ". Solo se reservará si Cantolagua publica exactamente esta"
                                + " actividad, día y hora.",
                            () -> addSlot(s));
                      },
                      17,
                      30,
                      true)
                  .show();
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private void addSlot(Plan.Slot slot) {
    List<Plan.Slot> slots = new ArrayList<>(recurring ? store.template() : store.week(week));
    for (Plan.Slot s : slots)
      if (s.key().equals(slot.key())) {
        toast("Ya está elegida");
        return;
      }
    slots.add(slot);
    persist(slots);
  }

  private void persist(List<Plan.Slot> slots) {
    try {
      store.slots(week, slots, recurring);
      changed();
    } catch (Exception e) {
      toast("No se pudo guardar el horario");
    }
  }

  private void changed() {
    Scheduler.refresh(this);
    render();
  }

  private void showHistory() {
    content.addView(text("Próximos intentos", 23, INK, true));
    List<Plan.Job> jobs = store.jobs();
    if (jobs.isEmpty())
      content.addView(
          text(
              "No hay intentos pendientes. Añade actividades o revisa la antelación configurada.",
              16,
              MUTED,
              false));
    int shown = 0;
    for (Plan.Job j : jobs) {
      if (shown++ >= 12) break;
      LinearLayout c = card();
      c.addView(text(j.label(), 18, INK, true));
      c.addView(
          text(
              (store.enabled() ? "Programada: " : "En pausa: ")
                  + j.release
                      .atZone(Plan.ZONE)
                      .format(DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm"))
                  + " · Madrid",
              15,
              MUTED,
              false));
    }
    if (store.lastWake() > 0)
      content.addView(
          text(
              "Última activación: "
                  + Instant.ofEpochMilli(store.lastWake())
                      .atZone(Plan.ZONE)
                      .format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")),
              14,
              MUTED,
              false));
    content.addView(text("Resultados", 23, INK, true));
    List<String[]> history = store.history();
    if (history.isEmpty())
      content.addView(
          text(
              "Aquí verás las reservas confirmadas y los intentos que necesiten tu atención.",
              16,
              MUTED,
              false));
    for (String[] h : history) {
      LinearLayout c = card();
      c.addView(text(h[1], 18, INK, true));
      c.addView(
          text(
              h[2],
              17,
              h[2].equals("Reservada") || h[2].equals("Ya reservada") ? GREEN : 0xff9b5722,
              true));
      c.addView(text(h[3], 15, MUTED, false));
      c.addView(button("Ver en Cantolagua", this::website, false));
      if (!h[2].equals("Reservada") && !h[2].equals("Ya reservada"))
        c.addView(
            button(
                "He revisado la web: permitir otro intento",
                () ->
                    confirm(
                        "Evitar duplicados",
                        "Confirma que has revisado la web y NO tienes esta reserva. Si la apertura"
                            + " aún está dentro del margen de 90 segundos, se permitirá otro"
                            + " intento; si ya pasó, no se recupera automáticamente.",
                        () -> {
                          store.retryAfterReview(h[0]);
                          changed();
                        }),
                false));
    }
    content.addView(
        text(
            "Solo se confirman plazas verificadas por la web. Las clases completas y las listas de"
                + " espera requieren tu intervención.",
            14,
            MUTED,
            false));
  }

  private EditText input(String hint, boolean password) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setTextSize(18);
    e.setTextColor(INK);
    e.setSingleLine(true);
    e.setMinHeight(dp(52));
    e.setInputType(
        password
            ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    return e;
  }

  private void showSettings() {
    LinearLayout c = card();
    c.addView(text("Tu cuenta de Cantolagua", 22, INK, true));
    c.addView(
        text(
            store.verified() ? "✓ Cuenta conectada" : "Conecta la cuenta que usas en la web",
            15,
            GREEN,
            true));
    EditText user = input("Usuario", false), pass = input("Contraseña", true);
    user.setText(store.user());
    c.addView(user);
    c.addView(pass);
    CheckBox visible = new CheckBox(this);
    visible.setText("Mostrar contraseña");
    visible.setTextColor(INK);
    visible.setOnCheckedChangeListener(
        (v, on) -> {
          pass.setInputType(
              InputType.TYPE_CLASS_TEXT
                  | (on
                      ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                      : InputType.TYPE_TEXT_VARIATION_PASSWORD));
          pass.setSelection(pass.length());
        });
    c.addView(visible);
    c.addView(
        button(
            busy ? "Conectando…" : "Guardar y comprobar acceso",
            () -> {
              String u = user.getText().toString().trim(), p = pass.getText().toString();
              if (u.isEmpty() || p.isEmpty()) {
                toast("Escribe el usuario y la contraseña");
                return;
              }
              background(
                  () -> {
                    ClubClient client = new ClubClient();
                    client.login(u, p);
                    List<ClubClient.Session> sessions = client.catalogue();
                    store.credentials(u, p);
                    store.catalogue(sessions);
                    store.status("Cuenta conectada. Elige tus clases y activa las reservas");
                  },
                  "Cuenta conectada. Las reservas siguen en pausa");
            },
            true));
    if (store.hasCredentials())
      c.addView(
          button(
              "Probar conexión y actualizar actividades",
              () ->
                  background(
                      () -> {
                        ClubClient client = new ClubClient();
                        client.login(store.user(), store.password());
                        store.catalogue(client.catalogue());
                      },
                      "Conexión correcta. Actividades actualizadas"),
              false));
    c.addView(
        text(
            "La contraseña se cifra con Android Keystore en este móvil. No se incluye en copias de"
                + " seguridad ni se envía a otro servidor.",
            14,
            MUTED,
            false));
    LinearLayout timing = card();
    timing.addView(text("Cuándo se abre la reserva", 22, INK, true));
    timing.addView(
        text(
            "Las reservas se abren 4 días naturales antes, a la misma hora de la sesión. Por"
                + " ejemplo: lunes 17:30 → jueves anterior 17:30.",
            15,
            MUTED,
            false));
    timing.addView(
        button(
            "Antelación: " + store.lead() + " días antes",
            () -> {
              EditText e = input("Días antes (0–14)", false);
              e.setInputType(InputType.TYPE_CLASS_NUMBER);
              e.setText(String.valueOf(store.lead()));
              new AlertDialog.Builder(this)
                  .setTitle("Días de antelación")
                  .setView(e)
                  .setPositiveButton(
                      "Guardar",
                      (d, w) -> {
                        try {
                          int lead = Integer.parseInt(e.getText().toString());
                          store.timing(lead);
                          changed();
                        } catch (Exception ex) {
                          toast("Elige un número entre 0 y 14");
                        }
                      })
                  .setNegativeButton("Cancelar", null)
                  .show();
            },
            false));
    timing.addView(text("Hora de apertura: la misma hora de cada actividad", 16, INK, true));
    timing.addView(
        text(
            "Zona horaria: Europe/Madrid. El cambio de hora de verano se ajusta automáticamente.",
            14,
            MUTED,
            false));
    LinearLayout permissions = card();
    permissions.addView(text("Preparar este móvil", 22, INK, true));
    permissions.addView(
        text(
            (Scheduler.allowed(this) ? "✓" : "○")
                + " Alarmas exactas\n"
                + (notificationsAllowed() ? "✓" : "○")
                + " Notificaciones\n"
                + (getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName())
                    ? "✓"
                    : "○")
                + " Batería sin restricciones",
            16,
            INK,
            false));
    if (Build.VERSION.SDK_INT >= 31)
      permissions.addView(
          button(
              "Permitir alarmas y recordatorios",
              () -> open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true),
              false));
    if (Build.VERSION.SDK_INT >= 33)
      permissions.addView(
          button(
              "Permitir notificaciones",
              () -> {
                if (notificationsAllowed()) open(Settings.ACTION_APP_NOTIFICATION_SETTINGS, false);
                else requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 5);
              },
              false));
    permissions.addView(
        button(
            "Revisar ahorro de batería",
            () -> open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, false),
            false));
    permissions.addView(
        button(
            "Ajustes de esta aplicación",
            () -> open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true),
            false));
    if (Build.VERSION.SDK_INT >= 33
        && getSystemService(PowerManager.class).isLowPowerStandbyEnabled())
      permissions.addView(
          text(
              "El modo de espera de bajo consumo está activo: puede cortar la conexión mientras la"
                  + " pantalla está apagada.",
              15,
              0xff9b5722,
              true));
    permissions.addView(
        text(
            "Puedes cerrar la app: Android la activará cerca de la apertura. Mantén el teléfono"
                + " encendido, con Internet y desbloquéalo una vez después de reiniciarlo. No"
                + " pulses «Forzar detención». Algunos móviles necesitan permitir el inicio"
                + " automático en sus ajustes de batería.",
            15,
            MUTED,
            false));
    LinearLayout help = card();
    help.addView(text("Mi Cantolagua · 1.0.0", 18, INK, true));
    help.addView(
        text(
            "Aplicación independiente. No garantiza plaza si la clase se llena. La solicitud se"
                + " prepara un minuto antes y se envía a partir de la hora configurada; Android, la"
                + " red y la web pueden retrasarla. No gestiona pagos ni añade automáticamente a"
                + " listas de espera.",
            14,
            MUTED,
            false));
    help.addView(button("Abrir la web de Cantolagua", this::website, false));
    help.addView(
        button(
            "Borrar cuenta y datos de este móvil",
            () ->
                confirm(
                    "Borrar los datos",
                    "Se pausarán las reservas y se eliminarán la contraseña, los horarios y el"
                        + " historial guardados en este móvil. Las reservas de la web se"
                        + " conservan.",
                    () -> {
                      try {
                        store.enabled(false);
                        Scheduler.refresh(this);
                        stopService(new Intent(this, BookingService.class));
                        store.erase();
                        render();
                      } catch (Exception e) {
                        toast("No se pudieron borrar todos los datos");
                      }
                    }),
            false));
  }

  private boolean notificationsAllowed() {
    return getSystemService(NotificationManager.class).areNotificationsEnabled();
  }

  private interface Work {
    void run() throws Exception;
  }

  private void background(Work work, String success) {
    if (busy) return;
    busy = true;
    render();
    worker.execute(
        () -> {
          String message;
          try {
            work.run();
            message = success;
          } catch (ClubClient.Problem e) {
            message = e.getMessage();
          } catch (Exception e) {
            message =
                "No se pudo conectar. Revisa Internet, tus datos de acceso y vuelve a intentarlo";
          }
          String finalMessage = message;
          runOnUiThread(
              () -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                Scheduler.refresh(this);
                render();
                new AlertDialog.Builder(this)
                    .setMessage(finalMessage)
                    .setPositiveButton("Entendido", null)
                    .show();
              });
        });
  }

  private void confirm(String title, String detail, Runnable action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(detail)
        .setPositiveButton("Confirmar", (d, w) -> action.run())
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private void open(String action, boolean packageUri) {
    try {
      Intent i = new Intent(action);
      if (packageUri) i.setData(Uri.parse("package:" + getPackageName()));
      if (action.equals(Settings.ACTION_APP_NOTIFICATION_SETTINGS))
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
      startActivity(i);
    } catch (Exception e) {
      toast("Abre los ajustes del teléfono y busca Mi Cantolagua");
    }
  }

  private void website() {
    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ClubClient.PAGE)));
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }
}
