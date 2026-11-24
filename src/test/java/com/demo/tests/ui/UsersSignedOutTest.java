package com.demo.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.api.ApiResponses;
import com.demo.api.ApiSpecs;
import com.demo.config.Environment;
import com.demo.config.Environment.LoginCredentials;
import com.demo.support.BaseUiTest;
import com.demo.ui.pages.UsersPage;
import io.qameta.allure.Feature;
import java.util.Objects;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

/**
 * Users-screen scenarios that start signed out. Logout revokes the API key on the server, so the
 * logout test signs in with its own session: logging out of the shared session would revoke the key
 * every other UI test uses.
 */
@Feature("Users screen")
public class UsersSignedOutTest extends BaseUiTest {

  private static final Pattern LOGIN_PAGE_URL = Pattern.compile("/index\\.html$");

  @Test(groups = "users", description = "sends a visitor without a login token to the login page")
  public void redirectsVisitorWithoutToken() {
    usersPage().goTo();

    expect().urlMatches(LOGIN_PAGE_URL);
    expect().visible(loginPage().heading);
  }

  @Test(
      groups = "users",
      description = "logs out, returns to the login form, and revokes the API key")
  public void logoutRevokesApiKey() {
    LoginCredentials credentials = Environment.requireLoginCredentials("this authenticated test");
    UsersPage usersPage = usersPage();
    loginPage().goTo();
    loginPage().login(credentials.username(), credentials.password());
    expect().visible(usersPage.heading);
    usersPage.waitUntilLoaded();
    String apiKey = usersPage.getStoredApiToken();
    assertThat(apiKey).isNotBlank();

    usersPage.logout();

    expect().urlMatches(LOGIN_PAGE_URL);
    expect().visible(loginPage().heading);
    expect()
        .poll(
            "the stored token to be removed", d -> usersPage.getStoredApiToken(), Objects::isNull);
    ApiResponses.assertStatus(ApiSpecs.request(apiKey).get("/api/users"), 401);
  }
}
