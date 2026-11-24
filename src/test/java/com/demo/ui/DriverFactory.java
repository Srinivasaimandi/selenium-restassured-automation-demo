package com.demo.ui;

import com.demo.config.Environment;
import java.time.Duration;
import java.util.Locale;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.testng.SkipException;

/**
 * Creates browser sessions. Selenium Manager downloads a matching driver (geckodriver or
 * chromedriver), so no driver setup is needed.
 *
 * <ul>
 *   <li>{@code -Dbrowser=safari} (default for local runs on macOS), {@code chrome} (default in CI
 *       and elsewhere) or {@code firefox}
 *   <li>{@code -Dheadless=false} to watch the browser (Safari is never headless)
 * </ul>
 */
public final class DriverFactory {

  /** The viewport of Playwright's "Desktop Chrome" device. */
  private static final int WIDTH = 1280;

  private static final int HEIGHT = 720;

  private DriverFactory() {}

  /**
   * The browser this run uses: {@code -Dbrowser} when set; otherwise Safari for local runs on
   * macOS, and Chrome in CI and everywhere else.
   */
  public static String browserName() {
    boolean macOs = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    String fallback = macOs && !Environment.isCi() ? "safari" : "chrome";
    return System.getProperty("browser", fallback).trim().toLowerCase(Locale.ROOT);
  }

  public static WebDriver create() {
    boolean headless = Boolean.parseBoolean(System.getProperty("headless", "true"));
    WebDriver driver =
        switch (browserName()) {
          case "firefox" -> firefox(headless);
          case "chrome" -> chrome(headless);
          case "safari" -> safari();
          default ->
              throw new IllegalArgumentException(
                  "Unsupported browser \"" + browserName() + "\". Use firefox, chrome or safari.");
        };
    driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
    return driver;
  }

  /** Safari allows only one automation session at a time, so its UI tests run one by one. */
  public static boolean allowsParallelSessions() {
    return !browserName().equals("safari");
  }

  /**
   * True when the session supports WebDriver BiDi, which network mocks and browser diagnostics
   * need. Safari does not support it yet.
   */
  public static boolean supportsBiDi(WebDriver driver) {
    return driver instanceof HasCapabilities withCapabilities
        && withCapabilities.getCapabilities().getCapability("webSocketUrl") instanceof String;
  }

  /** Skips the current test when the session has no WebDriver BiDi support. */
  public static void requireBiDi(WebDriver driver, String feature) {
    if (!supportsBiDi(driver)) {
      throw new SkipException(
          feature + " needs WebDriver BiDi, which " + browserName() + " does not support");
    }
  }

  /**
   * Safari has no headless mode and ignores BiDi. Enable automation once with {@code safaridriver
   * --enable} and Safari's Develop › Allow Remote Automation.
   */
  private static WebDriver safari() {
    WebDriver driver = new SafariDriver(new SafariOptions());
    driver.manage().window().setSize(new Dimension(WIDTH, HEIGHT));
    return driver;
  }

  private static WebDriver firefox(boolean headless) {
    FirefoxOptions options = new FirefoxOptions();
    if (headless) {
      options.addArguments("-headless");
    }
    options.addArguments("--width=" + WIDTH, "--height=" + HEIGHT);
    // WebDriver BiDi powers network mocking and console/network diagnostics.
    options.setCapability("webSocketUrl", true);
    return new FirefoxDriver(options);
  }

  private static WebDriver chrome(boolean headless) {
    ChromeOptions options = new ChromeOptions();
    if (headless) {
      options.addArguments("--headless=new");
    }
    options.addArguments("--window-size=" + WIDTH + "," + HEIGHT);
    if (Environment.isCi()) {
      options.addArguments("--no-sandbox", "--disable-dev-shm-usage");
    }
    options.setCapability("webSocketUrl", true);
    return new ChromeDriver(options);
  }
}
