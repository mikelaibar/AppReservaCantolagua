package es.cantolagua.misemana;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

public final class Store extends SQLiteOpenHelper {
  public static final Object LOCK = new Object();
  private final SharedPreferences prefs;

  public Store(Context c) {
    super(c, "bookings.db", null, 1);
    prefs = c.getSharedPreferences("private_settings", Context.MODE_PRIVATE);
  }

  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE attempts (account TEXT NOT NULL, job TEXT NOT NULL, session TEXT, label TEXT"
            + " NOT NULL, state TEXT NOT NULL, detail TEXT NOT NULL, stamp INTEGER NOT NULL,"
            + " PRIMARY KEY(account,job), UNIQUE(account,session))");
  }

  public void onUpgrade(SQLiteDatabase db, int from, int to) {
    throw new IllegalStateException("Versión de datos desconocida");
  }

  private void save(SharedPreferences.Editor e) {
    if (!e.commit()) throw new IllegalStateException("No se pudieron guardar los cambios");
  }

  public void woke() {
    save(prefs.edit().putLong("last_wake", System.currentTimeMillis()));
  }

  public long lastWake() {
    return prefs.getLong("last_wake", 0);
  }

  public boolean enabled() {
    return prefs.getBoolean("enabled", false);
  }

  public void enabled(boolean value) {
    synchronized (LOCK) {
      save(
          prefs
              .edit()
              .putBoolean("enabled", value)
              .putString("revision", UUID.randomUUID().toString()));
    }
  }

  public boolean verified() {
    return prefs.getBoolean("verified", false);
  }

  public String revision() {
    return prefs.getString("revision", "");
  }

  public String account() {
    return prefs.getString("account", "");
  }

  public String user() {
    return prefs.getString("user", "");
  }

  public boolean hasCredentials() {
    return prefs.contains("secret");
  }

  private static javax.crypto.SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias("cantolagua_login")) {
      KeyGenerator gen = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      gen.init(
          new KeyGenParameterSpec.Builder(
                  "cantolagua_login", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setUserAuthenticationRequired(false)
              .build());
      gen.generateKey();
    }
    return (javax.crypto.SecretKey) ks.getKey("cantolagua_login", null);
  }

  public void credentials(String user, String password) throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key());
    String secret = Base64.encodeToString(c.doFinal(password.getBytes("UTF-8")), Base64.NO_WRAP),
        iv = Base64.encodeToString(c.getIV(), Base64.NO_WRAP);
    byte[] digest =
        MessageDigest.getInstance("SHA-256")
            .digest(user.trim().toLowerCase(Locale.ROOT).getBytes("UTF-8"));
    StringBuilder account = new StringBuilder();
    for (byte b : digest) account.append(String.format(Locale.ROOT, "%02x", b & 255));
    synchronized (LOCK) {
      save(
          prefs
              .edit()
              .putString("user", user.trim())
              .putString("secret", secret)
              .putString("iv", iv)
              .putString("account", account.toString())
              .putString("revision", UUID.randomUUID().toString())
              .putBoolean("verified", true)
              .putBoolean("enabled", false));
    }
  }

  public String password() throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
    return new String(
        c.doFinal(Base64.decode(prefs.getString("secret", ""), Base64.NO_WRAP)), "UTF-8");
  }

  public int lead() {
    return prefs.getInt("lead", 4);
  }

  public void timing(int lead) {
    if (lead < 0 || lead > 14) throw new IllegalArgumentException();
    synchronized (LOCK) {
      save(prefs.edit().putInt("lead", lead).putString("revision", UUID.randomUUID().toString()));
    }
  }

  public List<Plan.Slot> template() {
    return Plan.decode(prefs.getString("template", "[]"));
  }

  public Map<String, List<Plan.Slot>> overrides() {
    Map<String, List<Plan.Slot>> out = new HashMap<>();
    try {
      JSONObject o = new JSONObject(prefs.getString("overrides", "{}"));
      Iterator<String> keys = o.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        out.put(k, Plan.decode(o.getJSONArray(k).toString()));
      }
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    return out;
  }

  public List<Plan.Slot> week(LocalDate monday) {
    Map<String, List<Plan.Slot>> m = overrides();
    return m.containsKey(monday.toString()) ? m.get(monday.toString()) : template();
  }

  public void slots(LocalDate monday, List<Plan.Slot> slots, boolean recurring) throws Exception {
    synchronized (LOCK) {
      SharedPreferences.Editor e = prefs.edit();
      JSONObject o = new JSONObject(prefs.getString("overrides", "{}"));
      if (recurring) {
        e.putString("template", Plan.encode(slots));
        o.remove(monday.toString());
      } else o.put(monday.toString(), new JSONArray(Plan.encode(slots)));
      save(
          e.putString("overrides", o.toString())
              .putString("revision", UUID.randomUUID().toString()));
    }
  }

  public void resetWeek(LocalDate monday) throws Exception {
    synchronized (LOCK) {
      JSONObject o = new JSONObject(prefs.getString("overrides", "{}"));
      o.remove(monday.toString());
      save(
          prefs
              .edit()
              .putString("overrides", o.toString())
              .putString("revision", UUID.randomUUID().toString()));
    }
  }

  public void catalogue(List<ClubClient.Session> sessions) throws Exception {
    synchronized (LOCK) {
      List<Timetable.Entry> history = Timetable.merge(timetable(), sessions);
      save(
          prefs
              .edit()
              .putString("catalogue", Timetable.encode(history))
              .putInt("catalogue_schema", 2)
              .putLong("catalogue_time", System.currentTimeMillis()));
    }
  }

  public List<Timetable.Entry> timetable() {
    try {
      return Timetable.decode(prefs.getString("catalogue", "[]"));
    } catch (JSONException e) {
      throw new IllegalStateException("No se pudo leer el horario guardado", e);
    }
  }

  public boolean currentCatalogue() {
    return prefs.getInt("catalogue_schema", 0) >= 2;
  }

  public List<Plan.Slot> catalogue() {
    List<Plan.Slot> slots = new ArrayList<>();
    for (Timetable.Entry e : timetable()) slots.add(e.slot);
    return slots;
  }

  public long catalogueTime() {
    return prefs.getLong("catalogue_time", 0);
  }

  public List<Plan.Job> jobs() {
    List<Plan.Job> jobs = Plan.jobs(Instant.now(), lead(), template(), overrides());
    jobs.removeIf(j -> attempted(j.key()));
    return jobs;
  }

  public boolean desired(Plan.Job job) {
    for (Plan.Slot s : week(Plan.monday(job.date))) if (s.key().equals(job.slot.key())) return true;
    return false;
  }

  public boolean attempted(String job) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT 1 FROM attempts WHERE account=? AND job=?",
                new String[] {account(), job})) {
      return c.moveToFirst();
    }
  }

  public boolean claim(String acct, Plan.Job job, String session) {
    ContentValues v = new ContentValues();
    v.put("account", acct);
    v.put("job", job.key());
    if (session != null) v.put("session", session);
    v.put("label", job.label());
    v.put("state", "Comprobar en la web");
    v.put(
        "detail",
        "Solicitud iniciada; si no aparece un resultado, verifica la web antes de repetir");
    v.put("stamp", System.currentTimeMillis());
    return getWritableDatabase()
            .insertWithOnConflict("attempts", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        != -1;
  }

  public void finish(String acct, Plan.Job job, String state, String detail) {
    ContentValues v = new ContentValues();
    v.put("state", state);
    v.put("detail", detail);
    v.put("stamp", System.currentTimeMillis());
    getWritableDatabase()
        .update("attempts", v, "account=? AND job=?", new String[] {acct, job.key()});
  }

  public List<String[]> history() {
    List<String[]> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT job,label,state,detail,stamp FROM attempts WHERE account=? ORDER BY stamp"
                    + " DESC LIMIT 100",
                new String[] {account()})) {
      while (c.moveToNext())
        out.add(
            new String[] {
              c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)
            });
    }
    return out;
  }

  public void retryAfterReview(String job) {
    synchronized (LOCK) {
      getWritableDatabase()
          .delete("attempts", "account=? AND job=?", new String[] {account(), job});
    }
  }

  public String status() {
    return prefs.getString("status", "Añade tu cuenta y elige tus clases para empezar");
  }

  public void status(String value) {
    save(prefs.edit().putString("status", value));
  }

  public void erase() throws Exception {
    synchronized (LOCK) {
      save(prefs.edit().clear());
      getWritableDatabase().delete("attempts", null, null);
      KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
      ks.load(null);
      ks.deleteEntry("cantolagua_login");
    }
  }
}
