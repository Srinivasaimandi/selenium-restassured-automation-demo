package com.demo.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Starts the bundled users app ({@code app/} submodule) for local runs, like Playwright's {@code
 * webServer} option. An app that is already running at {@code BASE_URL} is reused.
 */
public final class LocalApp {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);
  private static final Path APP_DIR = Path.of("app");
  private static final Path SERVER_LOG = Path.of("target", "app-server.log");
  private static Process server;

  private LocalApp() {}

  /** Starts the app when this run targets a loopback URL and nothing is listening there yet. */
  public static synchronized void startIfNeeded() {
    if (server != null || !Environment.startsLocalApp()) {
      return;
    }
    String baseUrl = Environment.baseUrl();
    if (isUp(baseUrl)) {
      System.out.println("[local-app] Reusing the app already running at " + baseUrl);
      return;
    }
    if (!Files.exists(APP_DIR.resolve("package.json"))) {
      throw new IllegalStateException(
          "app/ is not checked out. Run: git submodule update --init --remote app");
    }
    try {
      if (!Files.exists(APP_DIR.resolve("node_modules"))) {
        System.out.println("[local-app] Installing app dependencies (npm ci) ...");
        int exit = new ProcessBuilder(npm(), "ci", "--prefix", "app").inheritIO().start().waitFor();
        if (exit != 0) {
          throw new IllegalStateException("npm ci --prefix app failed with exit code " + exit);
        }
      }
      Files.createDirectories(SERVER_LOG.getParent());
      System.out.println(
          "[local-app] Starting the app at " + baseUrl + " (log: " + SERVER_LOG + ")");
      server =
          new ProcessBuilder(npm(), "--prefix", "app", "start")
              .redirectErrorStream(true)
              .redirectOutput(SERVER_LOG.toFile())
              .start();
      waitUntilUp(baseUrl);
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the app with npm", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while starting the app", e);
    }
  }

  /** Stops the app if this run started it. */
  public static synchronized void stop() {
    if (server == null) {
      return;
    }
    // npm starts node as a child process, so stop the whole process tree.
    server.descendants().forEach(ProcessHandle::destroy);
    server.destroy();
    server = null;
  }

  private static void waitUntilUp(String baseUrl) throws InterruptedException, IOException {
    Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      if (!server.isAlive()) {
        throw new IllegalStateException(
            "The app exited during startup. Last log lines:\n" + tail(SERVER_LOG));
      }
      if (isUp(baseUrl)) {
        return;
      }
      Thread.sleep(500);
    }
    stop();
    throw new IllegalStateException(
        "The app did not respond at " + baseUrl + " within " + STARTUP_TIMEOUT.toSeconds() + "s");
  }

  private static boolean isUp(String baseUrl) {
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
      HttpResponse<Void> response =
          client.send(
              HttpRequest.newBuilder(URI.create(baseUrl)).timeout(Duration.ofSeconds(2)).build(),
              HttpResponse.BodyHandlers.discarding());
      return response.statusCode() < 500;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String npm() {
    return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
        ? "npm.cmd"
        : "npm";
  }

  private static String tail(Path log) throws IOException {
    List<String> lines = Files.readAllLines(log);
    return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
  }
}
