package com.demo.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.api.ApiError;
import com.demo.api.ApiResponses;
import com.demo.api.ApiSpecs;
import com.demo.api.Json;
import com.demo.api.UsersApiClient;
import com.demo.config.Environment;
import com.demo.data.ApiUser;
import com.demo.data.NewUser;
import com.demo.data.TestData;
import com.demo.support.ApiTestUser;
import com.demo.support.BaseTest;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

/** Contract and CRUD coverage for the documented users REST API. */
@Feature("Users API")
public class UsersApiTest extends BaseTest {

  @Test(groups = "api", description = "GET /users rejects requests without an API key")
  public void listRejectsMissingApiKey() {
    ApiError error = ApiResponses.parseError(ApiSpecs.request().get("/api/users"), 401);

    assertThat(error.message()).isEqualTo("Unauthorized: Invalid API Key");
  }

  @Test(groups = "api", description = "GET /users returns the seeded users")
  public void listReturnsSeededUsers() {
    List<ApiUser> users = usersApi().listUsers();

    assertThat(users)
        .filteredOn(user -> user.username().equals(TestData.JANE.username()))
        .singleElement()
        .satisfies(user -> assertMatchesSeededJane(user));
  }

  @Test(groups = "api", description = "GET /users/search filters by email")
  public void searchFiltersByEmail() {
    List<ApiUser> users = usersApi().searchUsers(null, TestData.JANE.email());

    assertThat(users).hasSize(1);
    assertMatchesSeededJane(users.getFirst());
  }

  @Test(groups = "api", description = "GET /users/count returns a positive count")
  public void countIsPositive() {
    assertThat(usersApi().getUserCount()).isPositive();
  }

  @Test(
      groups = "api",
      description = "POST /users rejects incomplete payloads with a schema-valid error")
  public void createRejectsIncompletePayload() {
    Response response = usersApi().createUserResponse(Map.of());

    assertThat(ApiResponses.parseError(response, 400).message())
        .isEqualTo("All fields are required for each user");
  }

  @Test(
      groups = "api",
      description = "GET, PUT, and DELETE /users/{id} return schema-valid errors for missing users")
  public void missingUserReturns404() {
    int missing = TestData.MISSING_USER_ID;
    List<Response> responses =
        List.of(
            usersApi().getUserResponse(missing),
            usersApi().updateUserResponse(missing, Map.of("name", "Missing User")),
            usersApi().deleteUserResponse(missing));

    for (Response response : responses) {
      assertThat(ApiResponses.parseError(response, 404).message()).isEqualTo("User not found");
    }
  }

  @Test(
      groups = {"api", "test-data"},
      description = "GET /users/{id} returns a created user")
  public void getReturnsCreatedUser() {
    try (ApiTestUser created = createApiTestUser()) {
      ApiUser user = usersApi().getUser(created.id());

      assertThat(user)
          .isEqualTo(
              new ApiUser(created.id(), created.name(), created.email(), created.username()));
    }
  }

  @Test(
      groups = {"api", "test-data"},
      description = "PUT /users/{id} updates only the supplied fields of a created user")
  public void updateChangesOnlySuppliedFields() {
    try (ApiTestUser created = createApiTestUser()) {
      String updatedName = created.name() + " Updated";
      usersApi().updateUser(created.id(), Map.of("name", updatedName));

      assertThat(usersApi().getUser(created.id()))
          .isEqualTo(new ApiUser(created.id(), updatedName, created.email(), created.username()));
    }
  }

  @Test(
      groups = {"api", "test-data"},
      description = "DELETE /users/{id} removes a created user")
  public void deleteRemovesCreatedUser() {
    try (ApiTestUser created = createApiTestUser()) {
      usersApi().deleteUser(created.id());

      // The app assigns ids as max(id) + 1, so a user created by a parallel test can reuse this id
      // right after the delete. Assert on the deleted user's unique email instead.
      assertNoUserWithEmail(created.email());
    }
  }

  @Test(groups = "api", description = "POST /login rejects invalid credentials")
  public void loginRejectsInvalidCredentials() {
    Response response =
        ApiSpecs.request()
            .body(Map.of("username", "no-such-user", "password", "wrong-password"))
            .post("/api/login");

    assertThat(ApiResponses.parseError(response, 401).message()).isEqualTo("Invalid credentials");
  }

  @Test(groups = "api", description = "GET /users rejects an unknown API key")
  public void listRejectsUnknownApiKey() {
    Response response = ApiSpecs.request("not-a-real-api-key").get("/api/users");

    assertThat(ApiResponses.parseError(response, 401).message())
        .isEqualTo("Unauthorized: Invalid API Key");
  }

  @Test(groups = "api", description = "POST /logout revokes the API key it is sent with")
  public void logoutRevokesApiKey() {
    // usersApi() logs in for this test alone, so revoking its key affects no other test.
    usersApi().logout();

    Response response = usersApi().searchUsersResponse(null, null);
    assertThat(ApiResponses.parseError(response, 401).message())
        .isEqualTo("Unauthorized: Invalid API Key");
  }

  @Test(
      groups = "api",
      description = "GET /users/search returns 204 No Content when nothing matches")
  public void searchWithoutMatchesReturns204() {
    Response response = usersApi().searchUsersResponse(TestData.Searches.NO_RESULTS, null);

    assertThat(response.statusCode()).isEqualTo(204);
  }

  @Test(
      groups = {"api", "test-data"},
      description = "POST /users rejects a duplicate email")
  public void createRejectsDuplicateEmail() {
    try (ApiTestUser existing = createApiTestUser()) {
      NewUser duplicate = TestData.NEW_USER.unique().withEmail(existing.email());
      Response response = usersApi().createUserResponse(duplicate);
      deleteIfCreated(response);

      assertThat(ApiResponses.parseError(response, 400).message())
          .isEqualTo("Email " + existing.email() + " already exists");
    }
  }

  @Test(
      groups = {"api", "test-data"},
      description = "POST /users rejects a duplicate username")
  public void createRejectsDuplicateUsername() {
    try (ApiTestUser existing = createApiTestUser()) {
      NewUser duplicate = TestData.NEW_USER.unique().withUsername(existing.username());
      Response response = usersApi().createUserResponse(duplicate);
      deleteIfCreated(response);

      assertThat(ApiResponses.parseError(response, 400).message())
          .isEqualTo("Username " + existing.username() + " already exists");
    }
  }

  // Tagged test-data because a regression here would delete a real record.
  @Test(
      groups = {"api", "test-data"},
      description = "DELETE /users/{id} refuses to delete the protected seeded user")
  public void deleteRefusesProtectedUser() {
    Environment.assertTestDataTargetIsSafe();
    int protectedId = TestData.DELETE_PROTECTED_USER_ID;

    Response response = usersApi().deleteUserResponse(protectedId);

    assertThat(ApiResponses.parseError(response, 403).message())
        .isEqualTo("User with id " + protectedId + " is restricted from deleting");
    assertThat(usersApi().getUserStatus(protectedId)).isEqualTo(200);
  }

  /** Bulk creation; the API validates the whole batch and saves nothing if any user is rejected. */
  @Story("POST /users/bulk")
  @Test(
      groups = {"api", "test-data"},
      description = "POST /users/bulk creates every user in the batch")
  public void bulkCreatesEveryUser() {
    Environment.assertTestDataTargetIsSafe();
    List<NewUser> users = List.of(TestData.NEW_USER.unique(), TestData.NEW_USER.unique());

    try {
      List<ApiUser> created = usersApi().bulkAddUsers(users);

      assertThat(created).hasSize(users.size());
      for (int i = 0; i < users.size(); i++) {
        NewUser expected = users.get(i);
        ApiUser actual = created.get(i);
        ApiUser expectedUser =
            new ApiUser(actual.id(), expected.name(), expected.email(), expected.username());
        assertThat(actual).isEqualTo(expectedUser);
        assertThat(usersApi().getUser(actual.id())).isEqualTo(expectedUser);
      }
    } finally {
      usersApi().deleteUsersByEmail(users.stream().map(NewUser::email).toList());
    }
  }

  @Story("POST /users/bulk")
  @Test(
      groups = {"api", "test-data"},
      description =
          "POST /users/bulk rejects the batch and saves no one when a user is missing a field")
  public void bulkRejectsIncompleteUser() {
    Environment.assertTestDataTargetIsSafe();
    NewUser valid = TestData.NEW_USER.unique();
    NewUser incomplete = TestData.NEW_USER.unique().withPassword("");

    try {
      Response response = usersApi().bulkAddUsersResponse(List.of(valid, incomplete));

      assertThat(ApiResponses.parseError(response, 400).message())
          .isEqualTo("All fields are required for each user");
      assertNoUserWithEmail(valid.email());
    } finally {
      usersApi().deleteUsersByEmail(List.of(valid.email(), incomplete.email()));
    }
  }

  @Story("POST /users/bulk")
  @Test(
      groups = {"api", "test-data"},
      description = "POST /users/bulk rejects the batch when a user duplicates an existing email")
  public void bulkRejectsDuplicateEmail() {
    try (ApiTestUser existing = createApiTestUser()) {
      NewUser valid = TestData.NEW_USER.unique();
      NewUser duplicate = TestData.NEW_USER.unique().withEmail(existing.email());
      try {
        Response response = usersApi().bulkAddUsersResponse(List.of(valid, duplicate));

        assertThat(ApiResponses.parseError(response, 400).message())
            .isEqualTo("Email " + existing.email() + " already exists");
        assertNoUserWithEmail(valid.email());
      } finally {
        usersApi().deleteUsersByEmail(List.of(valid.email()));
      }
    }
  }

  @Story("POST /users/bulk")
  @Test(
      groups = {"api", "test-data"},
      description = "POST /users/bulk rejects a batch that repeats the same username")
  public void bulkRejectsRepeatedUsername() {
    Environment.assertTestDataTargetIsSafe();
    NewUser first = TestData.NEW_USER.unique();
    NewUser second = TestData.NEW_USER.unique().withUsername(first.username());

    try {
      Response response = usersApi().bulkAddUsersResponse(List.of(first, second));

      assertThat(ApiResponses.parseError(response, 400).message())
          .isEqualTo("Username " + first.username() + " already exists");
      assertNoUserWithEmail(first.email());
    } finally {
      usersApi().deleteUsersByEmail(List.of(first.email(), second.email()));
    }
  }

  private static void assertMatchesSeededJane(ApiUser user) {
    assertThat(user.name()).isEqualTo(TestData.JANE.name());
    assertThat(user.email()).isEqualTo(TestData.JANE.email());
    assertThat(user.username()).isEqualTo(TestData.JANE.username());
  }

  /** Asserts that no user with this email was saved. */
  private void assertNoUserWithEmail(String email) {
    assertThat(usersApi().searchUsersResponse(null, email).statusCode())
        .as("a user with email %s was saved", email)
        .isEqualTo(204);
  }

  /**
   * Removes a user that a rejection test created by mistake, so a regression does not leak data.
   */
  private void deleteIfCreated(Response response) {
    if (response.statusCode() == 201) {
      UsersApiClient api = usersApi();
      api.deleteUserIfPresent(Json.tree(response.asString()).path("id").asInt());
    }
  }
}
