package com.demo.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.data.TestData;
import com.demo.support.ApiTestUser;
import com.demo.support.BaseUiTest;
import com.demo.ui.pages.Row;
import com.demo.ui.pages.UsersPage;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import java.util.List;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

/** Authenticated scenarios for browsing and managing users. */
@Feature("Users screen")
public class UsersScreenTest extends BaseUiTest {

  @Test(
      groups = {"users", "smoke"},
      description = "shows the users table and management controls")
  public void showsTableAndControls() {
    UsersPage usersPage = openAuthenticatedUsersPage();

    expect().visible(usersPage.heading);
    expect().visible(usersPage.searchInput);
    expect().visible(usersPage.addUserButton);
    expect().visible(usersPage.refreshUsersButton);
    expect().visible(usersPage.resetDataButton);
    expect().visible(usersPage.logoutLink);
    expect().visible(usersPage.usersTable);
    expect().texts(usersPage.columnHeaders, List.of("#", "Name", "Email", "Username", "Actions"));
  }

  @Story("REQ-001: user search")
  @Test(groups = "users", description = "filters users by part of a name")
  public void filtersByPartialName() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    String partialName = TestData.Searches.JANE_BY_PARTIAL_NAME;
    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());
    int unfilteredCount = driver().findElements(usersPage.userRows).size();

    usersPage.searchFor(partialName);

    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());
    int filteredCount = driver().findElements(usersPage.userRows).size();
    assertThat(filteredCount).isLessThan(unfilteredCount);
    expect()
        .allTextsMatch(
            usersPage.columnCells("Name"),
            Pattern.compile(Pattern.quote(partialName), Pattern.CASE_INSENSITIVE));
  }

  @Story("REQ-001: user search")
  @Test(groups = "users", description = "filters users by email without case sensitivity")
  public void filtersByEmailIgnoringCase() {
    UsersPage usersPage = openAuthenticatedUsersPage();

    usersPage.searchFor(TestData.Searches.JANE_BY_EMAIL_CASE_INSENSITIVE);

    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());
    expect().count(usersPage.userRows, 1);
  }

  @Test(
      groups = {"users", "test-data"},
      description = "shows an API-created user in the users list")
  public void showsApiCreatedUser() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    try (ApiTestUser user = createApiTestUser()) {
      usersPage.refreshUsers();
      usersPage.searchFor(user.email());

      expect().visible(usersPage.userRow(user.name()).locator());
    }
  }

  @Story("REQ-001: user search")
  @Test(
      groups = "users",
      description = "shows no rows for an unmatched search and restores results when cleared")
  public void unmatchedSearchThenClear() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());
    int unfilteredCount = driver().findElements(usersPage.userRows).size();

    usersPage.searchFor(TestData.Searches.NO_RESULTS);
    expect().count(usersPage.userRows, 0);

    usersPage.searchFor("");
    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());
    expect().count(usersPage.userRows, unfilteredCount);
  }

  @Test(groups = "users", description = "opens the add-user form and can cancel without changes")
  public void addUserFormCanBeCancelled() {
    UsersPage usersPage = openAuthenticatedUsersPage();

    usersPage.openAddUserDialog();

    expect().visible(usersPage.addUserDialog);
    expect().visible(usersPage.addUserNameInput);
    expect().visible(usersPage.addUserEmailInput);
    expect().visible(usersPage.addUserUsernameInput);
    expect().visible(usersPage.addUserPasswordInput);
    expect().visible(usersPage.saveUserButton);

    usersPage.fillAddUserForm(TestData.NEW_USER);
    expect().value(usersPage.addUserNameInput, TestData.NEW_USER.name());
    usersPage.cancelAddUser();

    expect().hidden(usersPage.addUserDialog);
  }

  @Test(groups = "users", description = "shows validation when saving the add-user form empty")
  public void addUserFormValidation() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    usersPage.openAddUserDialog();
    usersPage.saveNewUser();

    expect().visible(usersPage.addUserValidationMessage);
    expect().visible(usersPage.addUserDialog);
    usersPage.cancelAddUser();
  }

  @Test(groups = "users", description = "opens a user row in edit mode with its existing values")
  public void editModeShowsExistingValues() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    TestData.SeededUser jane = TestData.JANE;
    usersPage.searchFor(jane.name());

    Row janeNameRow = usersPage.userRow(jane.name());
    expect().count(janeNameRow.locator(), 1);
    // Edit mode moves the name into a textbox, so keep hold of the row by its user ID.
    Row janeRow = usersPage.userRowById(usersPage.rowUserId(janeNameRow));
    usersPage.clickRowButton(janeRow, Row.EDIT);

    expect().count(janeRow.editFields(), 3);
    expect().value(janeRow.editField(0), jane.name());
    expect().value(janeRow.editField(1), jane.email());
    expect().value(janeRow.editField(2), jane.username());
  }
}
