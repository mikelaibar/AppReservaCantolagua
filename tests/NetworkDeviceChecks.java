package es.cantolagua.misemana;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;

/** Opt-in live read-only check. Isolated emulator only; no login and no reservations. */
public final class NetworkDeviceChecks extends Instrumentation {
  int passed;
  StringBuilder report = new StringBuilder();

  void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
    passed++;
    report.append("PASS ").append(message).append('\n');
  }

  View find(View view, String text) {
    if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View result = find(group.getChildAt(i), text);
        if (result != null) return result;
      }
    }
    return null;
  }

  public void onCreate(Bundle args) {
    super.onCreate(args);
    try {
      String proxy = args.getString("proxyHost");
      if (proxy != null) {
        java.net.Proxy route =
            new java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                new java.net.InetSocketAddress(
                    proxy, Integer.parseInt(args.getString("proxyPort"))));
        java.net.ProxySelector.setDefault(
            new java.net.ProxySelector() {
              public java.util.List<java.net.Proxy> select(java.net.URI uri) {
                return java.util.Collections.singletonList(route);
              }

              public void connectFailed(
                  java.net.URI uri, java.net.SocketAddress address, java.io.IOException error) {}
            });
      }
      // Cloud-only test configuration: trust the platform's verified proxy CA.
      // This uses the standard trust manager and keeps hostname verification enabled.
      // The release APK does not include this certificate or this configuration.
      if ("true".equals(args.getString("useRuntimeCa"))) {
        java.security.KeyStore trusted =
            java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        trusted.load(null, null);
        try (java.io.InputStream input = getContext().getAssets().open("runtime-proxy-ca.crt")) {
          trusted.setCertificateEntry(
              "runtime-proxy",
              java.security.cert.CertificateFactory.getInstance("X.509")
                  .generateCertificate(input));
        }
        javax.net.ssl.TrustManagerFactory managers =
            javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trusted);
        javax.net.ssl.SSLContext tls = javax.net.ssl.SSLContext.getInstance("TLS");
        tls.init(null, managers.getTrustManagers(), new java.security.SecureRandom());
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
      }
    } catch (Exception error) {
      Bundle failure = new Bundle();
      failure.putString(
          "stream",
          "FAIL test network configuration: "
              + error.getClass().getName()
              + ": "
              + error.getMessage());
      finish(Activity.RESULT_CANCELED, failure);
      return;
    }
    start();
  }

  public void onStart() {
    Bundle result = new Bundle();
    Store store = new Store(getTargetContext());
    try {
      store.erase();
      List<ClubClient.Session> actual = new ClubClient().publicCatalogue();
      check(actual.size() == 26, "live Android HTTP client downloads the 26 published sessions");
      check(
          actual.stream().map(session -> session.date).distinct().count() == 4,
          "live response includes all four published dates");
      check(
          actual.stream()
              .anyMatch(
                  session ->
                      session.date.getDayOfWeek() == DayOfWeek.TUESDAY
                          && session.time.equals("09:20")
                          && session.name.equals("Step-Training")),
          "Tuesday Step-Training at 09:20 is present in real Android network response");
      check(
          store.catalogueTime() == 0 && !store.hasCredentials(),
          "activity starts with an empty timetable and no account");
      Activity activity =
          startActivitySync(
              new Intent(getTargetContext(), MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      for (int n = 0; n < 180 && !store.currentCatalogue() && store.catalogueError().isEmpty(); n++)
        Thread.sleep(500);
      check(
          store.currentCatalogue(),
          "fresh installation loads the public calendar automatically: " + store.catalogueError());
      check(
          store.catalogueCount() == 26,
          "automatic refresh persists all 26 sessions without a seeded fixture");
      check(
          !store.hasCredentials() && !store.verified(),
          "viewing the calendar does not require saved credentials");
      Thread.sleep(500);
      waitForIdleSync();
      runOnMainSync(
          () -> {
            View label = find(activity.getWindow().getDecorView(), "Martes");
            find((View) label.getParent(), "+ Añadir actividad").performClick();
          });
      waitForIdleSync();
      Field field = MainActivity.class.getDeclaredField("picker");
      field.setAccessible(true);
      AlertDialog picker = (AlertDialog) field.get(activity);
      check(
          picker != null
              && picker.isShowing()
              && picker
                  .getListView()
                  .getAdapter()
                  .getItem(0)
                  .toString()
                  .equals("09:20 · Step-Training"),
          "Tuesday picker shows Step-Training loaded over the live network");
      android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
      if (screenshot != null) {
        try (FileOutputStream output =
            new FileOutputStream(
                new File(getTargetContext().getFilesDir(), "live-tuesday-1.0.2.png"))) {
          screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        }
        screenshot.recycle();
      }
      check(
          !store.enabled() && store.history().isEmpty(),
          "live checks leave reservations paused and send no bookings");
      result.putString(
          "stream",
          report
              + "RESULT: "
              + passed
              + " live Android network checks passed; public reads only.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putString(
          "stream",
          report
              + "FAIL "
              + error.getClass().getName()
              + ": "
              + error.getMessage()
              + "\n"
              + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
