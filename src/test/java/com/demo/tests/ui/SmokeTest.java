package com.demo.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.config.Environment;
import com.demo.support.BaseUiTest;
import com.demo.ui.BrowserDiagnostics;
import io.qameta.allure.Feature;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.Test;

/** Smoke checks for the real app home page and its Swagger navigation. */
@Feature("Application home smoke tests")
public class SmokeTest extends BaseUiTest {

  private static final By WELCOME_HEADING =
      By.xpath("//h1[normalize-space()='Welcome to Users App']");
  private static final By INTRO_TEXT =
      By.xpath(
          "//p[normalize-space()='Manage users, refresh data, and inspect the API from one place.']");
  private static final By UI_LINK = By.xpath("//a[normalize-space()='UI']");
  private static final By SWAGGER_LINK = By.xpath("//a[normalize-space()='Swagger Docs']");
  private static final By GRAPHQL_LINK = By.xpath("//a[normalize-space()='GraphQL']");

  @Test(groups = "smoke", description = "loads the application home page and login form")
  public void loadsHomePage() {
    try (BrowserDiagnostics diagnostics =
        new BrowserDiagnostics(driver(), List.of(Environment.baseUrl()))) {
      loginPage().goTo();

      expect().title("Users App");
      expect().visible(WELCOME_HEADING);
      expect().visible(INTRO_TEXT);
      expect().visible(UI_LINK);
      expect().attribute(SWAGGER_LINK, "href", Pattern.compile("/api-docs/?$"));
      expect().attribute(GRAPHQL_LINK, "href", Pattern.compile("/graphql$"));
      expect().visible(loginPage().heading);
      expect().visible(loginPage().usernameInput);
      expect().visible(loginPage().passwordInput);
      expect().enabled(loginPage().submitButton);
      diagnostics.assertNoErrors();
    }
  }

  @Test(
      groups = "smoke",
      enabled = false,
      description =
          "opens the Swagger documentation from the home page (skipped, as in the Playwright suite)")
  public void opensSwaggerDocs() {
    WebDriver driver = driver();
    loginPage().goTo();
    String href = driver.findElement(SWAGGER_LINK).getDomAttribute("href");
    assertThat(href).as("Swagger Docs link href").isNotNull();
    String docsUrl = URI.create(Environment.baseUrl()).resolve(href).toString();

    // BiDi listeners cover every tab, so this also sees errors in the docs tab as it loads.
    try (BrowserDiagnostics diagnostics =
        new BrowserDiagnostics(driver, List.of(Environment.baseUrl(), docsUrl))) {
      String home = driver.getWindowHandle();
      driver.findElement(SWAGGER_LINK).click();
      expect().poll("a new tab to open", d -> d.getWindowHandles().size(), n -> n == 2);
      List<String> handles = new ArrayList<>(driver.getWindowHandles());
      handles.remove(home);
      driver.switchTo().window(handles.getFirst());

      expect().urlMatches(Pattern.compile("/api-docs/$"));
      expect().title("Swagger UI");
      expect().visible(By.xpath("//h2[contains(., 'Users App API')]"));
      diagnostics.assertNoErrors();
    }
  }
}
