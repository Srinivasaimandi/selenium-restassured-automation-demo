package com.demo.ui.mocks;

import com.demo.api.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Records the method, URL and body of every {@code fetch} the page makes, by wrapping {@code
 * window.fetch}. WebDriver BiDi does not expose request bodies in Selenium yet, so tests that check
 * what the page sent use this. It lasts until the page navigates or reloads.
 */
public final class FetchRecorder {

  private static final String INSTALL =
      """
      window.__fetchLog = [];
      const originalFetch = window.fetch;
      window.fetch = (input, init = {}) => {
        window.__fetchLog.push({
          method: (init.method || 'GET').toUpperCase(),
          url: String(input),
          body: typeof init.body === 'string' ? init.body : null,
        });
        return originalFetch(input, init);
      };
      """;

  /** One recorded call. */
  public record FetchCall(String method, String url, String body) {
    /** The body parsed as JSON. */
    public JsonNode jsonBody() {
      return Json.tree(body);
    }
  }

  private final JavascriptExecutor js;

  private FetchRecorder(WebDriver driver) {
    this.js = (JavascriptExecutor) driver;
  }

  /** Starts recording on the current page. */
  public static FetchRecorder install(WebDriver driver) {
    FetchRecorder recorder = new FetchRecorder(driver);
    recorder.js.executeScript(INSTALL);
    return recorder;
  }

  /** Every call recorded so far with this method. */
  @SuppressWarnings("unchecked")
  public List<FetchCall> calls(String method) {
    List<Map<String, Object>> log =
        (List<Map<String, Object>>) js.executeScript("return window.__fetchLog || [];");
    return log.stream()
        .map(call ->
            new FetchCall(
                (String) call.get("method"), (String) call.get("url"), (String) call.get("body")))
        .filter(call -> call.method().equals(method))
        .toList();
  }
}
