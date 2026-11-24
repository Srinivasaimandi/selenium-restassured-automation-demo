package com.demo.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.log.LogLevel;
import org.openqa.selenium.bidi.log.StackFrame;
import org.openqa.selenium.bidi.log.StackTrace;
import org.openqa.selenium.bidi.module.LogInspector;
import org.openqa.selenium.bidi.module.Network;

/**
 * Collects console errors, uncaught page errors, failed requests and HTTP error responses from the
 * given origins, through WebDriver BiDi. Smoke tests assert none occurred.
 */
public final class BrowserDiagnostics implements AutoCloseable {

  private final Set<String> origins;
  private final List<String> errors = new CopyOnWriteArrayList<>();
  private final LogInspector logs;
  private final Network network;

  public BrowserDiagnostics(WebDriver driver, List<String> relevantUrls) {
    DriverFactory.requireBiDi(driver, "Console and network diagnostics");
    this.origins =
        relevantUrls.stream().map(BrowserDiagnostics::origin).collect(Collectors.toSet());
    this.logs = new LogInspector(driver);
    this.network = new Network(driver);

    logs.onConsoleEntry(entry -> {
      if (entry.getLevel() == LogLevel.ERROR && isRelevant(topFrameUrl(entry.getStackTrace()))) {
        errors.add("Console error: " + entry.getText());
      }
    });
    logs.onJavaScriptException(entry -> errors.add("Uncaught page error: " + entry.getText()));
    network.onFetchError(event -> {
      String url = event.getRequest().getUrl();
      if (isRelevant(url) && !"net::ERR_ABORTED".equals(event.getErrorText())) {
        errors.add(
            "Request failed: %s %s (%s)"
                .formatted(event.getRequest().getMethod(), url, event.getErrorText()));
      }
    });
    network.onResponseCompleted(event -> {
      String url = event.getRequest().getUrl();
      int status = event.getResponseData().getStatus();
      if (isRelevant(url) && status >= 400) {
        errors.add("HTTP %d: %s %s".formatted(status, event.getRequest().getMethod(), url));
      }
    });
  }

  /** Fails when any relevant error has been seen. */
  public void assertNoErrors() {
    assertThat(errors).as("Unexpected browser or same-origin network errors").isEmpty();
  }

  @Override
  public void close() {
    logs.close();
    network.close();
  }

  private boolean isRelevant(String url) {
    String origin = origin(url);
    return origin != null && origins.contains(origin);
  }

  /** Console errors without a stack frame (logged by the page itself) count as relevant. */
  private String topFrameUrl(StackTrace trace) {
    if (trace == null || trace.getCallFrames().isEmpty()) {
      return origins.iterator().next();
    }
    StackFrame top = trace.getCallFrames().getFirst();
    return top.getUrl();
  }

  private static String origin(String url) {
    try {
      URI uri = URI.create(url);
      if (uri.getScheme() == null || uri.getHost() == null) {
        return null;
      }
      return uri.getScheme()
          + "://"
          + uri.getHost()
          + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
