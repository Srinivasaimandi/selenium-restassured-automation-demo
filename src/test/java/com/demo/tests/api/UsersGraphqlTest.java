package com.demo.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.demo.api.GraphqlClient;
import com.demo.api.GraphqlClient.Documents;
import com.demo.api.GraphqlClient.GraphqlError;
import com.demo.api.GraphqlClient.GraphqlResult;
import com.demo.api.GraphqlClient.UserCountWithIds;
import com.demo.config.Environment;
import com.demo.data.ApiUser;
import com.demo.data.NewUser;
import com.demo.data.TestData;
import com.demo.support.ApiTestUser;
import com.demo.support.BaseTest;
import io.qameta.allure.Feature;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

/** Queries, mutations and error handling for the users GraphQL endpoint. */
@Feature("Users GraphQL API")
public class UsersGraphqlTest extends BaseTest {

  @Test(
      groups = {"api", "graphql"},
      description = "users returns the seeded users")
  public void usersReturnsSeededUsers() {
    assertThat(findJane(graphqlApi().listUsers()))
        .extracting(ApiUser::name, ApiUser::email)
        .containsExactly(TestData.JANE.name(), TestData.JANE.email());
  }

  @Test(
      groups = {"api", "graphql"},
      description = "user returns the user with the requested id")
  public void userReturnsRequestedUser() {
    ApiUser jane = findJane(graphqlApi().listUsers());

    assertThat(graphqlApi().getUser(jane.id())).isEqualTo(jane);
  }

  @Test(
      groups = {"api", "graphql"},
      description = "user returns null for an id that does not exist")
  public void userReturnsNullForMissingId() {
    assertThat(graphqlApi().getUser(TestData.MISSING_USER_ID)).isNull();
  }

  @Test(
      groups = {"api", "graphql"},
      description = "searchUsers filters by email without case sensitivity")
  public void searchUsersIgnoresEmailCase() {
    List<ApiUser> users =
        graphqlApi().searchUsers(null, TestData.Searches.JANE_BY_EMAIL_CASE_INSENSITIVE);

    assertThat(users).hasSize(1);
    assertThat(users.getFirst().username()).isEqualTo(TestData.JANE.username());
  }

  @Test(
      groups = {"api", "graphql"},
      description = "searchUsers returns an empty list when nothing matches")
  public void searchUsersReturnsEmptyList() {
    assertThat(graphqlApi().searchUsers(TestData.Searches.NO_RESULTS, null)).isEmpty();
  }

  @Test(
      groups = {"api", "graphql"},
      description = "userCount matches the number of users returned")
  public void userCountMatchesUsers() {
    // Other tests add and remove users in parallel, so retry until one read sees a stable dataset.
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          UserCountWithIds result = graphqlApi().getUserCountWithIds();
          assertThat(result.userCount()).isPositive();
          assertThat(result.userCount()).isEqualTo(result.ids().size());
        });
  }

  @Test(
      groups = {"api", "graphql"},
      description = "rejects a query for a field the schema does not define")
  public void rejectsUnknownField() {
    GraphqlResult result = graphqlApi().execute("query { noSuchField }", Map.of());

    assertThat(result.status()).isEqualTo(400);
    GraphqlError error = result.errors().getFirst();
    assertThat(error.message()).isEqualTo("Cannot query field \"noSuchField\" on type \"Query\".");
    assertThat(error.code()).isEqualTo("GRAPHQL_VALIDATION_FAILED");
  }

  @Test(
      groups = {"api", "graphql"},
      description = "deleteUser returns false for an id that does not exist")
  public void deleteUserReturnsFalseForMissingId() {
    assertThat(graphqlApi().deleteUser(TestData.MISSING_USER_ID)).isFalse();
  }

  @Test(
      groups = {"api", "graphql", "test-data"},
      description = "createUser adds a user that the REST API can read")
  public void createUserIsReadableOverRest() {
    Environment.assertTestDataTargetIsSafe();
    NewUser user = TestData.NEW_USER.unique();

    try {
      ApiUser created = graphqlApi().createUser(user);

      assertThat(created)
          .isEqualTo(new ApiUser(created.id(), user.name(), user.email(), user.username()));
      assertThat(usersApi().getUser(created.id())).isEqualTo(created);
    } finally {
      usersApi().deleteUsersByEmail(List.of(user.email()));
    }
  }

  @Test(
      groups = {"api", "graphql", "test-data"},
      description = "createUser rejects an email that already exists")
  public void createUserRejectsDuplicateEmail() {
    try (ApiTestUser existing = createApiTestUser()) {
      NewUser duplicate = TestData.NEW_USER.unique().withEmail(existing.email());
      try {
        GraphqlResult result =
            graphqlApi().execute(Documents.CREATE_USER, GraphqlClient.variables(duplicate));

        assertThat(result.data()).isNotNull();
        assertThat(result.data().isNull()).as("data is null").isTrue();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().message())
            .isEqualTo("Email " + duplicate.email() + " already exists");
        assertThat(result.errors().getFirst().code()).isEqualTo("BAD_USER_INPUT");
      } finally {
        // The duplicate's generated name is unique, so it finds any user saved by mistake.
        for (ApiUser leaked : graphqlApi().searchUsers(duplicate.name(), null)) {
          usersApi().deleteUserIfPresent(leaked.id());
        }
      }
    }
  }

  @Test(
      groups = {"api", "graphql", "test-data"},
      description = "deleteUser removes a user and reports success")
  public void deleteUserRemovesUser() {
    try (ApiTestUser created = createApiTestUser()) {
      assertThat(graphqlApi().deleteUser(created.id())).isTrue();

      // Assert on the deleted user's unique email, which no other test can create.
      assertThat(usersApi().searchUsersResponse(null, created.email()).statusCode())
          .as("user %s still exists", created.email())
          .isEqualTo(204);
    }
  }

  // Tagged test-data because a regression here would delete a real record.
  @Test(
      groups = {"api", "graphql", "test-data"},
      description = "deleteUser refuses to delete the protected seeded user")
  public void deleteUserRefusesProtectedUser() {
    Environment.assertTestDataTargetIsSafe();
    int protectedId = TestData.DELETE_PROTECTED_USER_ID;

    assertThat(graphqlApi().deleteUser(protectedId)).isFalse();
    assertThat(usersApi().getUserStatus(protectedId)).isEqualTo(200);
  }

  @Test(
      groups = {"api", "graphql"},
      description = "updateUser returns null for an id that does not exist")
  public void updateUserReturnsNullForMissingId() {
    assertThat(graphqlApi().updateUser(TestData.MISSING_USER_ID, Map.of("name", "Missing User")))
        .isNull();
  }

  @Test(
      groups = {"api", "graphql", "test-data"},
      description = "updateUser changes only the supplied fields")
  public void updateUserChangesOnlySuppliedFields() {
    try (ApiTestUser created = createApiTestUser()) {
      String updatedName = created.name() + " Updated";

      graphqlApi().updateUser(created.id(), Map.of("name", updatedName));

      assertThat(usersApi().getUser(created.id()))
          .isEqualTo(new ApiUser(created.id(), updatedName, created.email(), created.username()));
    }
  }

  @Test(
      groups = {"api", "graphql", "test-data"},
      description = "bulkAddUsers creates every user in the batch")
  public void bulkAddUsersCreatesEveryUser() {
    Environment.assertTestDataTargetIsSafe();
    List<NewUser> users = List.of(TestData.NEW_USER.unique(), TestData.NEW_USER.unique());

    try {
      List<ApiUser> created = graphqlApi().bulkAddUsers(users);

      assertThat(created)
          .extracting(ApiUser::email)
          .containsExactlyElementsOf(users.stream().map(NewUser::email).toList());
    } finally {
      usersApi().deleteUsersByEmail(users.stream().map(NewUser::email).toList());
    }
  }

  @Test(
      groups = {"api", "graphql"},
      description = "rejects requests without an API key")
  public void rejectsMissingApiKey() {
    GraphqlResult result = GraphqlClient.executeGraphql(Documents.USERS, Map.of(), null);

    // Apollo rejects a failed context check with 400, not 401, and returns no data.
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.data()).isNull();
    assertThat(result.errors()).extracting(GraphqlError::code).containsExactly("UNAUTHENTICATED");
  }

  private static ApiUser findJane(List<ApiUser> users) {
    return users.stream()
        .filter(user -> user.username().equals(TestData.JANE.username()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Seeded user Jane is missing from " + users));
  }
}
