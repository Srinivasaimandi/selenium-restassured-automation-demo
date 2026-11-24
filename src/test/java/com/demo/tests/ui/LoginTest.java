package com.demo.tests.ui;

import com.demo.config.Environment;
import com.demo.config.Environment.LoginCredentials;
import com.demo.support.BaseUiTest;
import com.demo.ui.pages.LoginPage;
import io.qameta.allure.Feature;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

/** Login form, validation, and successful authentication scenarios. */
@Feature("Login flow")
public class LoginTest extends BaseUiTest {

  @Test(
      groups = {"login", "smoke"},
      description = "shows the login form with its fields and an enabled submit button")
  public void showsLoginForm() {
    LoginPage login = loginPage();
    login.goTo();

    expect().visible(login.heading);
    expect().visible(login.usernameInput);
    expect().visible(login.passwordInput);
    expect().enabled(login.submitButton);
  }

  @Test(
      groups = {"login", "validation"},
      description = "requires username and password before submitting")
  public void requiresUsernameAndPassword() {
    LoginPage login = loginPage();
    login.goTo();

    login.submit();
    expect().focused(login.usernameInput);

    login.fillUsername("sample-user");
    login.submit();
    expect().focused(login.passwordInput);
    expect().visible(login.heading);
  }

  @Test(
      groups = {"login", "auth"},
      description = "logs in with valid credentials and opens the users list")
  public void logsInWithValidCredentials() {
    LoginCredentials credentials = Environment.requireLoginCredentials("this authenticated test");

    loginPage().goTo();
    loginPage().login(credentials.username(), credentials.password());

    expect().urlMatches(Pattern.compile("/usersPage\\.html$"));
    expect().visible(usersPage().heading);
    expect().visible(usersPage().logoutLink);
  }
}
