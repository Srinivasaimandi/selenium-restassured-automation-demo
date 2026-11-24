package com.demo.tests.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.config.Environment;
import com.demo.config.Environment.LoginCredentials;
import com.demo.support.AuthState;
import com.demo.support.BaseUiTest;
import io.qameta.allure.Feature;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

/**
 * Logs in through the UI once and saves the session token for the other UI tests, like Playwright's
 * {@code auth.setup.ts} project. It runs first, in its own suite section.
 */
@Feature("Setup")
public class AuthSetupTest extends BaseUiTest {

  @Test(groups = "setup", description = "authenticate and save the browser session token")
  public void authenticateAndSaveSession() {
    LoginCredentials credentials =
        Environment.requireLoginCredentials("setup and save the authenticated session");

    loginPage().goTo();
    loginPage().login(credentials.username(), credentials.password());
    expect().urlMatches(Pattern.compile("/usersPage\\.html$"));
    expect().visible(usersPage().heading);
    expect().visible(usersPage().logoutLink);

    String token = usersPage().getStoredApiToken();
    assertThat(token).as("x-api-token in localStorage").isNotBlank();
    AuthState.save(token);
  }
}
