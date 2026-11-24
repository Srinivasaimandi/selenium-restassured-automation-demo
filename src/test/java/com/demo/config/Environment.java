package com.demo.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.testng.SkipException;

/**
 * Settings for the selected test environment.
 *
 * <p>{@code TEST_ENV} picks the environment (local, test, dev or staging; default local). Values
 * are read from, in order: a JVM system property, a process environment variable, then {@code
 * env/.env.<TEST_ENV>} (or {@code env/.env} for local).
 */
public final class Environment {

  private static final Set<String> SUPPORTED_ENVIRONMENTS =
      Set.of("local", "test", "dev", "staging");
  private static final Set<String> LOOPBACK_HOSTS =
      Set.of("localhost", "127.0.0.1", "::1", "[::1]");
  private static final String TEST_ENV = readTestEnv();
  private static final Dotenv ENV_FILE = loadEnvFile();

  private Environment() {}

  /** Account credentials read from the selected environment. */
  public record LoginCredentials(String username, String password) {}

  /** The selected environment name. */
  public static String testEnv() {
    return TEST_ENV;
  }

  /** Reads a setting, or returns null when it is not set anywhere. */
  public static String get(String key) {
    String value = System.getProperty(key);
    if (isBlank(value)) {
      value = System.getenv(key);
    }
    if (isBlank(value) && ENV_FILE != null) {
      value = ENV_FILE.get(key, null);
    }
    return isBlank(value) ? null : value;
  }

  /** The application URL for this run, without a trailing slash. */
  public static String baseUrl() {
    String url = get("BASE_URL");
    if (url == null && TEST_ENV.equals("local")) {
      url = "http://127.0.0.1:9899";
    }
    if (url == null) {
      throw new IllegalStateException(
          "BASE_URL is required for the \"%s\" environment. Set it in env/.env.%s or in the process environment."
              .formatted(TEST_ENV, TEST_ENV));
    }
    return url.replaceAll("/+$", "");
  }

  /** Returns login credentials when both values are configured. */
  public static Optional<LoginCredentials> loginCredentials() {
    String username = get("LOGIN_USERNAME");
    String password = get("LOGIN_PASSWORD");
    if (username == null || password == null) {
      return Optional.empty();
    }
    return Optional.of(new LoginCredentials(username, password));
  }

  /** Returns login credentials, or skips the current test when they are not configured. */
  public static LoginCredentials requireLoginCredentials(String purpose) {
    return loginCredentials()
        .orElseThrow(
            () -> new SkipException("Set LOGIN_USERNAME and LOGIN_PASSWORD to run " + purpose));
  }

  /** True when running in CI. */
  public static boolean isCi() {
    return get("CI") != null;
  }

  /** True when the run should start the bundled app because it targets a loopback URL. */
  public static boolean startsLocalApp() {
    return (TEST_ENV.equals("local") || TEST_ENV.equals("test"))
        && LOOPBACK_HOSTS.contains(hostname(baseUrl()));
  }

  /**
   * Prevents API test-data mutations outside loopback and explicitly allowed test hosts.
   *
   * @throws IllegalStateException when the target is not safe to change
   */
  public static void assertTestDataTargetIsSafe() {
    String hostname = hostname(baseUrl());
    if (TEST_ENV.equals("local") && LOOPBACK_HOSTS.contains(hostname)) {
      return;
    }
    List<String> allowedTestHosts =
        Arrays.stream(Optional.ofNullable(get("TEST_DATA_ALLOWED_HOSTS")).orElse("").split(","))
            .map(host -> host.trim().toLowerCase(Locale.ROOT))
            .filter(host -> !host.isEmpty())
            .toList();
    if (TEST_ENV.equals("test") && allowedTestHosts.contains(hostname)) {
      return;
    }
    throw new IllegalStateException(
        ("API test data is restricted to loopback local targets or allowlisted TEST_ENV=test hosts;"
                + " \"%s\" is not allowed.")
            .formatted(hostname));
  }

  private static String hostname(String url) {
    return URI.create(url).getHost().toLowerCase(Locale.ROOT);
  }

  private static String readTestEnv() {
    String value = System.getProperty("TEST_ENV");
    if (isBlank(value)) {
      value = System.getenv("TEST_ENV");
    }
    String env = isBlank(value) ? "local" : value.trim();
    if (!SUPPORTED_ENVIRONMENTS.contains(env)) {
      throw new IllegalStateException(
          "Unsupported TEST_ENV \"%s\". Choose one of: local, test, dev, staging.".formatted(env));
    }
    return env;
  }

  private static Dotenv loadEnvFile() {
    Path envFile = Path.of("env", ".env." + TEST_ENV);
    if (!Files.exists(envFile) && TEST_ENV.equals("local")) {
      envFile = Path.of("env", ".env");
    }
    if (!Files.exists(envFile)) {
      return null;
    }
    return Dotenv.configure()
        .directory(envFile.getParent().toString())
        .filename(envFile.getFileName().toString())
        .ignoreIfMalformed()
        .load();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
