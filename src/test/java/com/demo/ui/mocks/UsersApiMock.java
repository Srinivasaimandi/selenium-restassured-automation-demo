package com.demo.ui.mocks;

import com.demo.api.Json;
import com.demo.config.Environment;
import com.demo.data.ApiUser;
import com.demo.ui.DriverFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.module.Network;
import org.openqa.selenium.bidi.network.AddInterceptParameters;
import org.openqa.selenium.bidi.network.BeforeRequestSent;
import org.openqa.selenium.bidi.network.BytesValue;
import org.openqa.selenium.bidi.network.ContinueRequestParameters;
import org.openqa.selenium.bidi.network.Header;
import org.openqa.selenium.bidi.network.InterceptPhase;
import org.openqa.selenium.bidi.network.ProvideResponseParameters;
import org.openqa.selenium.bidi.network.UrlPattern;

/**
 * Network mocks for the users API, built on WebDriver BiDi request interception: the Selenium
 * counterpart of Playwright's {@code page.route()}.
 *
 * <p>Each mock matches one endpoint and HTTP method; every other request falls through to the next
 * mock or the real app. Mocks added later take precedence over earlier ones. Interception starts
 * when the first mock is added, and every same-origin request the page sends is also recorded.
 */
public final class UsersApiMock implements AutoCloseable {

  /** The users-API endpoints the users screen calls, matched by URL path. */
  public enum Endpoint {
    /** {@code GET} lists users; {@code POST} creates one. */
    LIST(path -> path.equals("/api/users")),
    /** {@code PUT} updates and {@code DELETE} removes the user with the ID in the path. */
    USER(path -> path.matches("/api/users/\\d+")),
    /** {@code PUT} restores the seeded data. */
    RESET_DATA(path -> path.equals("/api/users/reset-data"));

    private final Predicate<String> pathMatcher;

    Endpoint(Predicate<String> pathMatcher) {
      this.pathMatcher = pathMatcher;
    }
  }

  /** A request the page sent, as seen by the mock. */
  public record InterceptedRequest(String method, String url) {
    public String path() {
      return URI.create(url).getPath();
    }
  }

  /** A request the mock is holding back, to check the page while its response is pending. */
  public final class HeldRequest {
    private final CompletableFuture<Intercepted> routed = new CompletableFuture<>();

    /** Waits until the page has sent the request, and returns it. */
    public InterceptedRequest received() {
      return await(routed).request();
    }

    /** Answers the held request with this response. */
    public void release(MockResponse response) {
      provide(await(routed).id(), response);
    }

    /** Lets the held request reach the app. */
    public void release() {
      network.continueRequest(new ContinueRequestParameters(await(routed).id()));
    }
  }

  private record Intercepted(String id, InterceptedRequest request) {}

  /** Handles a matching request; returns false to fall back to earlier mocks or the app. */
  private interface Handler {
    boolean handle(Intercepted request);
  }

  private record Route(Endpoint endpoint, String method, Handler handler) {}

  private final WebDriver driver;
  private final List<Route> routes = new CopyOnWriteArrayList<>();
  private final List<InterceptedRequest> requests = new CopyOnWriteArrayList<>();
  // BiDi events arrive on Selenium's connection thread; answering on our own thread keeps that
  // thread free to receive the command responses.
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private Network network;

  public UsersApiMock(WebDriver driver) {
    this.driver = driver;
  }

  /** Answers every matching request with a fixed response. */
  public void fulfill(Endpoint endpoint, String method, MockResponse response) {
    intercept(
        endpoint,
        method,
        request -> {
          provide(request.id(), response);
          return true;
        });
  }

  /** Answers user-list requests with these users. */
  public void respondWithUsers(List<ApiUser> users) {
    fulfill(Endpoint.LIST, "GET", MockResponse.json(200, users));
  }

  /** Fails every matching request at the network level, as if the connection dropped. */
  public void abort(Endpoint endpoint, String method) {
    intercept(
        endpoint,
        method,
        request -> {
          network.failRequest(request.id());
          return true;
        });
  }

  /**
   * Holds the next matching request until the test releases it, to check the page while a response
   * is slow. Later matching requests are not held.
   */
  public HeldRequest hold(Endpoint endpoint, String method) {
    HeldRequest held = new HeldRequest();
    intercept(endpoint, method, request -> held.routed.complete(request));
    return held;
  }

  /**
   * Every same-origin request the page has sent since the first mock was added, or since {@link
   * #startRecording()}.
   */
  public List<InterceptedRequest> requests() {
    return Collections.unmodifiableList(new ArrayList<>(requests));
  }

  /** Starts intercepting and recording requests without adding a mock. */
  public void startRecording() {
    start();
  }

  @Override
  public void close() {
    executor.shutdownNow();
    if (network != null) {
      network.close();
    }
  }

  private void intercept(Endpoint endpoint, String method, Handler handler) {
    start();
    routes.add(new Route(endpoint, method, handler));
  }

  private synchronized void start() {
    if (network != null) {
      return;
    }
    DriverFactory.requireBiDi(driver, "Network mocking");
    URI base = URI.create(Environment.baseUrl());
    UrlPattern sameOrigin = new UrlPattern().protocol(base.getScheme()).hostname(base.getHost());
    if (base.getPort() != -1) {
      sameOrigin.port(String.valueOf(base.getPort()));
    }
    network = new Network(driver);
    network.onBeforeRequestSent(event -> executor.execute(() -> route(event)));
    network.addIntercept(
        new AddInterceptParameters(InterceptPhase.BEFORE_REQUEST_SENT).urlPattern(sameOrigin));
  }

  private void route(BeforeRequestSent event) {
    if (!event.isBlocked()) {
      return;
    }
    var data = event.getRequest();
    Intercepted request =
        new Intercepted(
            data.getRequestId(), new InterceptedRequest(data.getMethod(), data.getUrl()));
    requests.add(request.request());
    String path = request.request().path();
    List<Route> newestFirst = new ArrayList<>(routes);
    Collections.reverse(newestFirst);
    for (Route route : newestFirst) {
      if (route.method().equals(request.request().method())
          && route.endpoint().pathMatcher.test(path)
          && route.handler().handle(request)) {
        return;
      }
    }
    network.continueRequest(new ContinueRequestParameters(request.id()));
  }

  private void provide(String requestId, MockResponse response) {
    ProvideResponseParameters parameters =
        new ProvideResponseParameters(requestId)
            .statusCode(response.status())
            .reasonPhrase(response.reasonPhrase());
    if (response.contentType() != null) {
      parameters.headers(
          List.of(
              new Header(
                  "Content-Type", new BytesValue(BytesValue.Type.STRING, response.contentType()))));
    }
    // Always send a body, even an empty one: Chrome does not answer a request whose provided
    // response has none.
    parameters.body(
        new BytesValue(BytesValue.Type.STRING, response.body() == null ? "" : response.body()));
    network.provideResponse(parameters);
  }

  private static <T> T await(CompletableFuture<T> future) {
    try {
      return future.get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new AssertionError("The page did not send the held request within 10s", e);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** A canned response: a JSON or raw body with its content type, or no body. */
  public record MockResponse(int status, String contentType, String body) {

    public static MockResponse json(int status, Object body) {
      return new MockResponse(status, "application/json", Json.write(body));
    }

    public static MockResponse empty(int status) {
      return new MockResponse(status, null, null);
    }

    public static MockResponse raw(int status, String contentType, String body) {
      return new MockResponse(status, contentType, body);
    }

    String reasonPhrase() {
      return switch (status) {
        case 200 -> "OK";
        case 201 -> "Created";
        case 204 -> "No Content";
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 500 -> "Internal Server Error";
        default -> "Status " + status;
      };
    }
  }
}
