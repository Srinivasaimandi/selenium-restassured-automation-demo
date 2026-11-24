package com.demo.tests.ui;

import com.demo.data.NewUser;
import com.demo.data.TestData;
import com.demo.support.BaseUiTest;
import com.demo.ui.mocks.UsersApiMock;
import com.demo.ui.mocks.UsersApiMock.Endpoint;
import com.demo.ui.mocks.UsersApiMock.MockResponse;
import com.demo.ui.pages.UsersPage;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

/** The users screen's behaviour when the users API fails, simulated with network mocks. */
@Feature("Users screen")
@Story("When the users API fails")
public class UsersApiFailureTest extends BaseUiTest {

  @Test(
      groups = {"users", "mocked"},
      description = "shows a fetch error when the users request fails at the network level")
  public void networkFailureOnList() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());
    usersApiMock().abort(Endpoint.LIST, "GET");

    usersPage.refreshUsers();

    expect().visible(usersPage.fetchFailedMessage);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows a fetch error when the users API returns a server error page")
  public void serverErrorPageOnList() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    usersApiMock()
        .fulfill(
            Endpoint.LIST,
            "GET",
            MockResponse.raw(500, "text/html", "<h1>Internal Server Error</h1>"));

    usersPage.refreshUsers();

    expect().visible(usersPage.fetchFailedMessage);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "returns to the login page and drops the token when the API key is rejected")
  public void rejectedApiKeyReturnsToLogin() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    usersApiMock()
        .fulfill(
            Endpoint.LIST,
            "GET",
            MockResponse.json(401, Map.of("message", "Unauthorized: Invalid API Key")));

    usersPage.refreshUsers();

    expect().urlMatches(Pattern.compile("/index\\.html$"));
    expect().visible(loginPage().heading);
    expect()
        .poll(
            "the stored token to be removed", d -> usersPage.getStoredApiToken(), Objects::isNull);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows an empty-state message when the API returns no users")
  public void noContentShowsEmptyState() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    usersApiMock().fulfill(Endpoint.LIST, "GET", MockResponse.empty(204));

    usersPage.refreshUsers();

    expect().visible(usersPage.noUsersMessage);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows an empty-state message when the API returns an empty list")
  public void emptyListShowsEmptyState() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    usersApiMock().respondWithUsers(List.of());

    usersPage.refreshUsers();

    expect().visible(usersPage.noUsersMessage);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "keeps the add-user dialog open and shows the server rejection message")
  public void addUserRejectedByServer() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    NewUser user = TestData.NEW_USER.unique();
    String rejection = "Email " + user.email() + " already exists";
    usersApiMock()
        .fulfill(Endpoint.LIST, "POST", MockResponse.json(400, Map.of("message", rejection)));

    usersPage.openAddUserDialog();
    usersPage.fillAddUserForm(user);
    usersPage.saveNewUser();

    expect().visible(UsersPage.addUserError(rejection));
    expect().visible(usersPage.addUserDialog);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows a generic error when saving a user fails at the network level")
  public void addUserNetworkFailure() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    UsersApiMock mock = usersApiMock();
    mock.abort(Endpoint.LIST, "POST");

    usersPage.openAddUserDialog();
    usersPage.fillAddUserForm(TestData.NEW_USER.unique());
    usersPage.saveNewUser();

    expect().visible(UsersPage.addUserError("Failed to add user."));
    expect().visible(usersPage.addUserDialog);
  }
}
