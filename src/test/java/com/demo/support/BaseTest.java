package com.demo.support;

import com.demo.api.GraphqlClient;
import com.demo.api.UsersApiClient;
import com.demo.config.Environment;
import com.demo.data.NewUser;
import com.demo.data.TestData;
import org.testng.annotations.AfterMethod;

/**
 * API resources available to every test, like the Playwright API fixtures. Each test gets its own
 * logged-in clients, created on first use, because TestNG runs methods of one instance in parallel.
 */
public abstract class BaseTest {

  private static final ThreadLocal<UsersApiClient> USERS_API = new ThreadLocal<>();
  private static final ThreadLocal<GraphqlClient> GRAPHQL_API = new ThreadLocal<>();

  /** A REST client with its own API key; skips the test without login credentials. */
  protected UsersApiClient usersApi() {
    if (USERS_API.get() == null) {
      USERS_API.set(UsersApiClient.login(Environment.requireLoginCredentials("API tests")));
    }
    return USERS_API.get();
  }

  /** A GraphQL client with its own API key; skips the test without login credentials. */
  protected GraphqlClient graphqlApi() {
    if (GRAPHQL_API.get() == null) {
      GRAPHQL_API.set(GraphqlClient.login(Environment.requireLoginCredentials("GraphQL tests")));
    }
    return GRAPHQL_API.get();
  }

  /**
   * Creates a disposable user through the API, after checking the target is safe to change. Close
   * it (try-with-resources) to delete it.
   */
  protected ApiTestUser createApiTestUser() {
    Environment.assertTestDataTargetIsSafe();
    NewUser user = TestData.NEW_USER.unique();
    return new ApiTestUser(user, usersApi().createUser(user).id(), usersApi());
  }

  @AfterMethod(alwaysRun = true)
  public void releaseApiClients() {
    USERS_API.remove();
    GRAPHQL_API.remove();
  }
}
