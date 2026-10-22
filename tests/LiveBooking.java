package es.cantolagua.misemana;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.net.ssl.*;

/** Explicit opt-in real booking diagnostic. Credentials only come from environment bindings. */
public final class LiveBooking {
  static void network() throws Exception {
    String proxy = System.getenv("HTTPS_PROXY");
    if (proxy != null && !proxy.isEmpty()) {
      URI uri = new URI(proxy);
      Proxy route = new Proxy(Proxy.Type.HTTP,
          new InetSocketAddress(uri.getHost(), uri.getPort() < 0 ? 80 : uri.getPort()));
      ProxySelector.setDefault(new ProxySelector() {
        public List<Proxy> select(URI target) { return Collections.singletonList(route); }
        public void connectFailed(URI uri, SocketAddress address, IOException error) {}
      });
    }
    String ca = System.getenv("CANTOLAGUA_TEST_CA_FILE");
    if (ca != null && !ca.isEmpty()) {
      KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
      trusted.load(null, null);
      try (InputStream input = Files.newInputStream(Paths.get(ca))) {
        trusted.setCertificateEntry("runtime-proxy", java.security.cert.CertificateFactory
            .getInstance("X.509").generateCertificate(input));
      }
      TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      managers.init(trusted);
      SSLContext tls = SSLContext.getInstance("TLS");
      tls.init(null, managers.getTrustManagers(), new SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
      // The default HTTPS hostname verifier remains enabled.
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 3 || args.length > 4
        || (args.length == 4 && !args[3].equals("--reserve") && !args[3].equals("--waitlist")))
      throw new IllegalArgumentException("Usage: LiveBooking YYYY-MM-DD HH:mm activity [--reserve|--waitlist]");
    LocalDate date = LocalDate.parse(args[0]);
    String time = LocalTime.parse(args[1]).toString();
    Plan.Job job = new Plan.Job(new Plan.Slot(date.getDayOfWeek().getValue() - 1,
        args[2], "Actividad seleccionada", time), date, 4);
    if (!date.atTime(LocalTime.parse(time)).atZone(Plan.ZONE).toInstant().isAfter(Instant.now()))
      throw new IllegalArgumentException("The session has already started");
    String user = System.getenv("CANTOLAGUA_USERNAME"), password = System.getenv("CANTOLAGUA_PASSWORD");
    if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
      System.err.println("BLOCKED: CANTOLAGUA_USERNAME and CANTOLAGUA_PASSWORD are not injected. No login or booking sent.");
      System.exit(2);
    }
    network();
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(
        (user.trim().toLowerCase(Locale.ROOT) + "|" + job.key()).getBytes("UTF-8"));
    StringBuilder id = new StringBuilder();
    for (byte b : digest) id.append(String.format(Locale.ROOT, "%02x", b & 255));
    Path dir = Paths.get("/workspace/.cantolagua-live-checks");
    Files.createDirectories(dir);
    Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    Path state = dir.resolve(id + ".state");
    try (FileChannel channel = FileChannel.open(dir.resolve(id + ".lock"),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.tryLock()) {
      if (lock == null) throw new IllegalStateException("Another diagnostic owns this session");
      ClubClient client = new ClubClient();
      client.login(user, password);
      password = null;
      ClubClient.Session session = ClubClient.match(client.catalogue(), job);
      if (session == null) {
        System.out.println("NOT SENT: exact date, activity and hour were not published."); return;
      }
      System.out.println("Authenticated session: " + session.date + " " + session.time
          + " · " + session.name + " · " + session.status);
      if (session.booked) {
        System.out.println("VERIFIED: already enrolled; no additional booking sent."); return;
      }
      if (session.waitlisted) {
        System.out.println("WAITLIST VERIFIED: already on the waiting list; no additional request sent."); return;
      }
      if (Files.exists(state)) {
        System.out.println("BLOCKED: a previous mutation was recorded; review the website before any further attempt."); return;
      }
      boolean join = args.length == 4 && args[3].equals("--waitlist") && !session.open;
      if (join) {
        Map<String, String> q = ClubClient.query(ClubClient.safe(session.href, true));
        join = !session.paid && "apuntarlistaesperaclase".equals(q.get("a"))
            && session.id.equals(q.get("R"));
      }
      if ((!session.open && !join) || args.length != 4) {
        System.out.println("NOT SENT: " + session.detail + (args.length == 3 ? " (read-only mode)" : "")); return;
      }
      // Durable marker written before the one authorized mutation. Never automatically replay it.
      Files.write(state, Collections.singletonList("started " + session.id + " " + job.key()),
          StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      ClubClient.Outcome result = join ? client.joinWaitlistVerified(session, job)
          : client.reserveVerified(session, job);
      Files.write(state, Collections.singletonList((result.confirmed ? "confirmed " : "uncertain ")
          + session.id + " " + job.key()), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      System.out.println((result.confirmed
          ? (result.waitlisted ? "WAITLIST VERIFIED: " : "BOOKING VERIFIED: ")
          : "UNCERTAIN: ") + result.detail);
      if (!result.confirmed) System.exit(3);
    }
  }
}
