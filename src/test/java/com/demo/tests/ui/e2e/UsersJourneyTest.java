package com.demo.tests.ui.e2e;

import com.demo.config.Environment;
import com.demo.config.Environment.LoginCredentials;
import com.demo.data.ApiUser;
import com.demo.data.NewUser;
import com.demo.data.TestData;
import com.demo.support.BaseUiTest;
import com.demo.ui.pages.Row;
import com.demo.ui.pages.UsersPage;
import io.qameta.allure.Feature;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

/**
 * End-to-end journeys for authentication and user management. They log in through the form, so they
 * start signed out. The first one logs out, which revokes its own API key, not the shared one.
 */
@Feature("Users end-to-end journey")
public class UsersJourneyTest extends BaseUiTest {

  private static final Pattern USERS_PAGE_URL = Pattern.compile("/usersPage\\.html$");

  @Test(groups = "e2e", description = "logs in, searches for a user, and logs out")
  public void loginSearchLogout() {
    LoginCredentials credentials = Environment.requireLoginCredentials("this end-to-end test");
    UsersPage usersPage = usersPage();

    loginPage().goTo();
    loginPage().login(credentials.username(), credentials.password());
    expect().urlMatches(USERS_PAGE_URL);
    expect().visible(usersPage.heading);

    usersPage.searchFor(TestData.JANE.email());
    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());

    usersPage.logout();
    expect().urlMatches(Pattern.compile("/index\\.html$"));
    expect().visible(loginPage().heading);
  }

  @Test(
      groups = {"e2e", "test-data"},
      description = "logs in, adds a user, updates it, and deletes it")
  public void addUpdateDeleteUser() {
    Environment.assertTestDataTargetIsSafe();
    LoginCredentials credentials = Environment.requireLoginCredentials("this end-to-end test");
    UsersPage usersPage = usersPage();
    NewUser newUser = TestData.NEW_USER.unique();
    String updatedName = newUser.name() + " Updated";

    try {
      loginPage().goTo();
      loginPage().login(credentials.username(), credentials.password());
      expect().urlMatches(USERS_PAGE_URL);
      expect().visible(usersPage.heading);

      usersPage.openAddUserDialog();
      usersPage.fillAddUserForm(newUser);
      usersPage.saveNewUser();

      expect().hidden(usersPage.addUserDialog);
      expect().visible(usersPage.userRow(newUser.name()).locator());

      // Editing moves the name into a textbox, so keep hold of the row by its user ID.
      Row newUserRow =
          usersPage.userRowById(usersPage.rowUserId(usersPage.userRow(newUser.name())));
      usersPage.clickRowButton(newUserRow, Row.EDIT);
      usersPage.fillRowEditField(newUserRow, 0, updatedName);
      usersPage.clickRowButton(newUserRow, Row.SAVE);

      Row updatedRow = usersPage.userRow(updatedName);
      expect().visible(updatedRow.locator());
      expect().containsText(updatedRow.locator(), newUser.email());

      usersPage.deleteRow(updatedRow);
      expect().count(usersPage.userRow(updatedName).locator(), 0);
    } finally {
      for (ApiUser user : usersApi().listUsers()) {
        if (user.email().equals(newUser.email())) {
          usersApi().deleteUserIfPresent(user.id());
        }
      }
    }
  }
}
