package com.demo.support;

import com.demo.config.Environment;
import com.demo.ui.DriverFactory;
import com.demo.ui.Expect;
import com.demo.ui.mocks.UsersApiMock;
import com.demo.ui.pages.LoginPage;
import com.demo.ui.pages.UsersPage;
import com.deque.html.axecore.selenium.AxeBuilder;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.regex.Pattern;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * A fresh Chrome session per test, with page objects, waiting assertions and network mocks, like
 * the Playwright UI fixtures. Tests start signed out; call {@link #openAuthenticatedUsersPage()} to
 * start signed in with the shared session.
 */
public abstract class BaseUiTest extends BaseTest {

  /** WCAG 2.1 A and AA rule tags checked by accessibility tests. */
  private static final List<String> WCAG_TAGS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");

  private static final Pattern USERS_PAGE_URL = Pattern.compile("/usersPage\\.html$");

  private record Session(
      WebDriver driver,
      LoginPage loginPage,
      UsersPage usersPage,
      Expect expect,
      UsersApiMock mock) {}

  private static final ThreadLocal<Session> SESSION = new ThreadLocal<>();

  @BeforeMethod(alwaysRun = true)
  public void startBrowser() {
    WebDriver driver = DriverFactory.create();
    SESSION.set(
        new Session(
            driver,
            new LoginPage(driver),
            new UsersPage(driver),
            new Expect(driver),
            new UsersApiMock(driver)));
  }

  @AfterMethod(alwaysRun = true)
  public void stopBrowser(ITestResult result) {
    Session session = SESSION.get();
    SESSION.remove();
    if (session == null) {
      return;
    }
    try {
      if (result.getStatus() == ITestResult.FAILURE) {
        attachFailureEvidence(session.driver());
      }
      session.mock().close();
    } finally {
      session.driver().quit();
    }
  }

  protected WebDriver driver() {
    return SESSION.get().driver();
  }

  protected LoginPage loginPage() {
    return SESSION.get().loginPage();
  }

  protected UsersPage usersPage() {
    return SESSION.get().usersPage();
  }

  protected Expect expect() {
    return SESSION.get().expect();
  }

  protected UsersApiMock usersApiMock() {
    return SESSION.get().mock();
  }

  /** An axe-core scan limited to WCAG 2.1 A and AA rules. */
  protected AxeBuilder axe() {
    return new AxeBuilder().withTags(WCAG_TAGS);
  }

  /**
   * Opens the users page signed in with the shared session token. Skips the test without login
   * credentials.
   */
  protected UsersPage openAuthenticatedUsersPage() {
    String token = AuthState.apiToken();
    // localStorage is per origin, so load a page of the app before writing the token.
    driver().get(Environment.baseUrl() + "/index.html");
    ((JavascriptExecutor) driver())
        .executeScript("localStorage.setItem('x-api-token', arguments[0]);", token);
    usersPage().goTo();
    expect().visible(usersPage().heading);
    expect().urlMatches(USERS_PAGE_URL);
    return usersPage();
  }

  private static void attachFailureEvidence(WebDriver driver) {
    try {
      byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
      Allure.attachment(
          "Screenshot on failure",
          "image/png",
          new ByteArrayInputStream(screenshot),
          AttachmentOptions.withFileExtension("png"));
      Allure.attachment(
          "Page source on failure",
          "text/html",
          driver.getPageSource(),
          AttachmentOptions.withFileExtension("html"));
    } catch (RuntimeException e) {
      System.err.println("[ui] Could not capture failure evidence: " + e.getMessage());
    }
  }
}
