package com.demo.tests.ui;

import static com.demo.data.TestData.MockUsers.ADMIN;
import static com.demo.data.TestData.MockUsers.ALICE;
import static com.demo.data.TestData.MockUsers.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import com.demo.api.Json;
import com.demo.data.ApiUser;
import com.demo.support.BaseUiTest;
import com.demo.ui.mocks.FetchRecorder;
import com.demo.ui.mocks.UsersApiMock;
import com.demo.ui.mocks.UsersApiMock.Endpoint;
import com.demo.ui.mocks.UsersApiMock.HeldRequest;
import com.demo.ui.mocks.UsersApiMock.InterceptedRequest;
import com.demo.ui.mocks.UsersApiMock.MockResponse;
import com.demo.ui.pages.Row;
import com.demo.ui.pages.UsersPage;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Row actions and loading behaviour against mocked users. The mocked users have IDs no real record
 * uses, so a request that escapes its mock cannot change seeded data.
 */
@Feature("Users screen")
@Story("With mocked users")
public class MockedUsersTest extends BaseUiTest {

  @BeforeMethod(alwaysRun = true)
  public void showMockedUsers() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    usersApiMock().respondWithUsers(List.of(ADMIN, ALICE, BOB));
    usersPage.refreshUsers();
    expect().visible(usersPage.userRowById(ALICE.id()).locator());
  }

  @Test(
      groups = {"users", "mocked"},
      description = "lists exactly the users the API returns, except the admin account")
  public void listsUsersExceptAdmin() {
    UsersPage usersPage = usersPage();

    expect().texts(usersPage.columnCells("Name"), List.of(ALICE.name(), BOB.name()));
    expect().count(usersPage.userRowById(ADMIN.id()).locator(), 0);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows no users until a slow users response arrives")
  public void showsNoUsersUntilSlowResponseArrives() {
    UsersPage usersPage = usersPage();
    HeldRequest usersRequest = usersApiMock().hold(Endpoint.LIST, "GET");

    driver().navigate().refresh();
    usersRequest.received();
    expect().visible(usersPage.heading);
    expect().count(usersPage.userRows, 0);

    usersRequest.release(MockResponse.json(200, List.of(ALICE, BOB)));

    expect().count(usersPage.userRows, 2);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "disables save while an inline edit is in flight, then shows the saved values")
  public void disablesSaveWhileEditInFlight() {
    UsersPage usersPage = usersPage();
    ApiUser updated = ALICE.withName(ALICE.name() + " Updated");
    Row row = usersPage.userRowById(ALICE.id());
    HeldRequest updateRequest = usersApiMock().hold(Endpoint.USER, "PUT");
    FetchRecorder fetches = FetchRecorder.install(driver());

    usersPage.clickRowButton(row, Row.EDIT);
    usersPage.fillRowEditField(row, 0, updated.name());
    usersPage.clickRowButton(row, Row.SAVE);

    InterceptedRequest request = updateRequest.received();
    assertThat(request.path()).isEqualTo("/api/users/" + ALICE.id());
    JsonNode body = fetches.calls("PUT").getFirst().jsonBody();
    assertThat(body)
        .isEqualTo(
            Json.MAPPER.valueToTree(
                Map.of(
                    "name", updated.name(), "email", ALICE.email(), "username", ALICE.username())));
    expect().disabled(row.button(Row.SAVE));

    updateRequest.release(MockResponse.json(200, updated));

    expect().visible(row.cell(updated.name()));
    expect().count(row.editFields(), 0);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows the server message when an inline edit is rejected")
  public void inlineEditRejected() {
    UsersPage usersPage = usersPage();
    String rejection = "Email " + BOB.email() + " already exists";
    usersApiMock()
        .fulfill(Endpoint.USER, "PUT", MockResponse.json(400, Map.of("message", rejection)));
    Row row = usersPage.userRowById(ALICE.id());

    usersPage.clickRowButton(row, Row.EDIT);
    usersPage.fillRowEditField(row, 1, BOB.email());
    usersPage.clickRowButton(row, Row.SAVE);

    expect().visible(UsersPage.statusMessage(rejection));
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows a generic error when an inline edit fails at the network level")
  public void inlineEditNetworkFailure() {
    UsersPage usersPage = usersPage();
    usersApiMock().abort(Endpoint.USER, "PUT");
    Row row = usersPage.userRowById(ALICE.id());

    usersPage.clickRowButton(row, Row.EDIT);
    usersPage.clickRowButton(row, Row.SAVE);

    expect().visible(UsersPage.statusMessage("Failed to update user."));
  }

  // The "#" column's numbers are drawn by a CSS counter, which is not in the DOM, so this test
  // checks what the numbering depends on: row order, and that the column shows no user IDs.
  @Test(
      groups = {"users", "mocked"},
      description = "shows a row-number column instead of user IDs")
  public void showsRowNumbersInsteadOfIds() {
    UsersPage usersPage = usersPage();

    expect().texts(usersPage.columnCells("Name"), List.of(ALICE.name(), BOB.name()));
    expect().texts(usersPage.columnCells("#"), List.of("", ""));
    for (ApiUser user : List.of(ALICE, BOB)) {
      expect()
          .count(
              By.xpath("//table[@id='users-table']//td[normalize-space()='" + user.id() + "']"), 0);
    }
  }

  @Test(
      groups = {"users", "mocked"},
      description = "asks for confirmation naming the user before deleting")
  public void asksForDeleteConfirmation() {
    UsersPage usersPage = usersPage();

    usersPage.clickRowButton(usersPage.userRowById(ALICE.id()), Row.DELETE);

    expect().visible(usersPage.deleteUserDialog);
    expect()
        .text(
            usersPage.deleteUserMessage,
            "Delete %s (%s)? This can't be undone.".formatted(ALICE.name(), ALICE.username()));
    expect().focused(usersPage.cancelDeleteButton);
  }

  @Test(
      groups = {"users", "mocked"},
      description = "keeps the row and sends no request when the delete is cancelled")
  public void cancelledDeleteSendsNoRequest() {
    UsersPage usersPage = usersPage();
    UsersApiMock mock = usersApiMock();
    Row row = usersPage.userRowById(ALICE.id());

    usersPage.clickRowButton(row, Row.DELETE);
    usersPage.cancelDelete();

    expect().hidden(usersPage.deleteUserDialog);
    expect().visible(row.locator());
    assertThat(mock.requests()).noneMatch(request -> request.method().equals("DELETE"));
  }

  @Test(
      groups = {"users", "mocked"},
      description = "removes a row once the API confirms the delete")
  public void removesRowAfterDelete() {
    UsersPage usersPage = usersPage();
    usersApiMock().fulfill(Endpoint.USER, "DELETE", MockResponse.empty(204));

    usersPage.deleteRow(usersPage.userRowById(ALICE.id()));

    expect().hidden(usersPage.deleteUserDialog);
    expect().count(usersPage.userRowById(ALICE.id()).locator(), 0);
    expect().visible(usersPage.userRowById(BOB.id()).locator());
    expect().texts(usersPage.columnCells("Name"), List.of(BOB.name()));
  }

  @Test(
      groups = {"users", "mocked"},
      description = "keeps a row and shows the server message when a delete is refused")
  public void deleteRefused() {
    UsersPage usersPage = usersPage();
    String refusal = "User with id " + ALICE.id() + " is restricted from deleting";
    usersApiMock()
        .fulfill(Endpoint.USER, "DELETE", MockResponse.json(403, Map.of("message", refusal)));
    Row row = usersPage.userRowById(ALICE.id());

    usersPage.deleteRow(row);

    expect().visible(UsersPage.statusMessage(refusal));
    expect().visible(row.locator());
  }

  @Test(
      groups = {"users", "mocked"},
      description =
          "keeps a row and shows a generic error when a delete fails at the network level")
  public void deleteNetworkFailure() {
    UsersPage usersPage = usersPage();
    usersApiMock().abort(Endpoint.USER, "DELETE");
    Row row = usersPage.userRowById(ALICE.id());

    usersPage.deleteRow(row);

    expect().visible(UsersPage.statusMessage("Failed to delete user."));
    expect().visible(row.locator());
  }

  // The reset-data mock must answer before the request reaches the app: a real reset deletes
  // users that tests running in parallel depend on.
  @Test(
      groups = {"users", "mocked"},
      description = "reloads the users list after a data reset succeeds")
  public void reloadsListAfterReset() {
    UsersPage usersPage = usersPage();
    UsersApiMock mock = usersApiMock();
    mock.fulfill(
        Endpoint.RESET_DATA,
        "PUT",
        MockResponse.json(200, Map.of("message", "Data has been reset from backup.")));
    mock.respondWithUsers(List.of(ALICE));
    int requestsBefore = mock.requests().size();

    usersPage.resetData();

    expect()
        .poll(
            "the page to reload the users list",
            d -> mock.requests().subList(requestsBefore, mock.requests().size()),
            sent ->
                sent.stream()
                    .anyMatch(r -> r.method().equals("GET") && r.path().equals("/api/users")));
    expect().count(usersPage.userRows, 1);
    expect().visible(usersPage.userRowById(ALICE.id()).locator());
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows an error when a data reset returns a server error")
  public void resetServerError() {
    UsersPage usersPage = usersPage();
    String failure = "Failed to reset data.";
    usersApiMock()
        .fulfill(
            Endpoint.RESET_DATA,
            "PUT",
            MockResponse.json(
                500, Map.of("message", failure, "error", "ENOENT: data-backup.json not found")));

    usersPage.resetData();

    expect().visible(UsersPage.statusMessage(failure));
  }

  @Test(
      groups = {"users", "mocked"},
      description = "shows an error when a data reset fails at the network level")
  public void resetNetworkFailure() {
    UsersPage usersPage = usersPage();
    usersApiMock().abort(Endpoint.RESET_DATA, "PUT");

    usersPage.resetData();

    expect().visible(UsersPage.statusMessage("Failed to reset data."));
  }
}
