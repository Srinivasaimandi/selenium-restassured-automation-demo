package com.demo.api;

import com.demo.config.Environment.LoginCredentials;
import com.demo.data.ApiUser;
import com.demo.data.NewUser;
import com.fasterxml.jackson.core.type.TypeReference;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authenticated client for the documented Users REST API. */
public final class UsersApiClient {

  /**
   * Every REST operation this client calls, as {@code METHOD /path} in the OpenAPI document's
   * notation. The OpenAPI contract test checks this list against the published document in both
   * directions.
   */
  public static final List<String> OPERATIONS =
      List.of(
          "POST /login",
          "POST /logout",
          "GET /users",
          "POST /users",
          "GET /users/search",
          "GET /users/count",
          "PUT /users/reset-data",
          "POST /users/bulk",
          "GET /users/{id}",
          "PUT /users/{id}",
          "DELETE /users/{id}");

  private static final TypeReference<List<ApiUser>> USER_LIST = new TypeReference<>() {};

  private final String apiKey;

  private UsersApiClient(String apiKey) {
    this.apiKey = apiKey;
  }

  /** Logs in and returns the validated API key used by the REST and GraphQL clients. */
  public static String requestApiKey(LoginCredentials credentials) {
    Response response =
        ApiSpecs.request()
            .body(Map.of("username", credentials.username(), "password", credentials.password()))
            .post("/api/login");
    return ApiResponses.parse(response, 200, Schemas.API_KEY, ApiKey.class).apiKey();
  }

  /** Logs in and returns a client that sends the resulting API key. */
  @Step("Log in to the users API")
  public static UsersApiClient login(LoginCredentials credentials) {
    return new UsersApiClient(requestApiKey(credentials));
  }

  /** Revokes this client's API key; every later call with it is rejected with 401. */
  public void logout() {
    ApiResponses.assertStatus(request().post("/api/logout"), 204);
  }

  /** Returns every user after validating the response. */
  public List<ApiUser> listUsers() {
    return ApiResponses.parse(request().get("/api/users"), 200, Schemas.API_USERS, USER_LIST);
  }

  /** Searches users by optional (nullable) name and email values. */
  public List<ApiUser> searchUsers(String name, String email) {
    return ApiResponses.parse(searchUsersResponse(name, email), 200, Schemas.API_USERS, USER_LIST);
  }

  /** Sends an authenticated search request and returns its unparsed response. */
  public Response searchUsersResponse(String name, String email) {
    Map<String, String> params = new LinkedHashMap<>();
    if (name != null) {
      params.put("name", name);
    }
    if (email != null) {
      params.put("email", email);
    }
    return request().queryParams(params).get("/api/users/search");
  }

  /** Returns the number of users stored by the API. */
  public int getUserCount() {
    return ApiResponses.parse(
            request().get("/api/users/count"), 200, Schemas.API_USER_COUNT, UserCount.class)
        .count();
  }

  /** Returns a validated user record by ID. */
  public ApiUser getUser(int id) {
    return ApiResponses.parse(getUserResponse(id), 200, Schemas.API_USER, ApiUser.class);
  }

  /** Sends an authenticated user lookup and returns its unparsed response. */
  public Response getUserResponse(int id) {
    return request().get("/api/users/{id}", id);
  }

  /** Returns the HTTP status for a user lookup, including expected 404s. */
  public int getUserStatus(int id) {
    return getUserResponse(id).statusCode();
  }

  /** Creates a user and validates the API's 201 response. */
  public ApiUser createUser(NewUser user) {
    return ApiResponses.parse(createUserResponse(user), 201, Schemas.API_USER, ApiUser.class);
  }

  /** Sends an authenticated create request with any body and returns its unparsed response. */
  public Response createUserResponse(Object body) {
    return request().body(body).post("/api/users");
  }

  /** Creates every user in one request and validates the API's 201 response. */
  public List<ApiUser> bulkAddUsers(List<NewUser> users) {
    return ApiResponses.parse(bulkAddUsersResponse(users), 201, Schemas.API_USERS, USER_LIST);
  }

  /** Sends an authenticated bulk-create request and returns its unparsed response. */
  public Response bulkAddUsersResponse(List<NewUser> users) {
    return request().body(users).post("/api/users/bulk");
  }

  /**
   * Restores the app's seeded data, deleting every user added since. This affects every test
   * running against the same app, so only the destructive suite, which runs last, may call it.
   */
  public String resetData() {
    return ApiResponses.parse(
            request().put("/api/users/reset-data"), 200, Schemas.API_MESSAGE, ApiMessage.class)
        .message();
  }

  /** Updates the supplied fields and validates the returned user. */
  public ApiUser updateUser(int id, Map<String, String> fields) {
    return ApiResponses.parse(updateUserResponse(id, fields), 200, Schemas.API_USER, ApiUser.class);
  }

  /** Sends an authenticated update request and returns its unparsed response. */
  public Response updateUserResponse(int id, Map<String, String> fields) {
    return request().body(fields).put("/api/users/{id}", id);
  }

  /** Deletes a user and requires the documented 204 response. */
  public void deleteUser(int id) {
    ApiResponses.assertStatus(deleteUserResponse(id), 204);
  }

  /** Sends an authenticated delete request and returns its unparsed response. */
  public Response deleteUserResponse(int id) {
    return request().delete("/api/users/{id}", id);
  }

  /**
   * Deletes every user whose email exactly matches one of the given emails. Used to clean up after
   * create calls whose response cannot be trusted to list what was saved.
   */
  @Step("Clean up users by email")
  public void deleteUsersByEmail(Collection<String> emails) {
    for (String email : emails) {
      Response response = searchUsersResponse(null, email);
      if (response.statusCode() == 204) {
        continue;
      }
      for (ApiUser user : ApiResponses.parse(response, 200, Schemas.API_USERS, USER_LIST)) {
        if (user.email().equals(email)) {
          deleteUserIfPresent(user.id());
        }
      }
    }
  }

  /** Deletes a user while tolerating a prior successful deletion. */
  public void deleteUserIfPresent(int id) {
    Response response = deleteUserResponse(id);
    if (response.statusCode() != 404) {
      ApiResponses.assertStatus(response, 204);
    }
  }

  private RequestSpecification request() {
    return ApiSpecs.request(apiKey);
  }

  private record ApiKey(String apiKey) {}

  private record ApiMessage(String message) {}

  private record UserCount(int count) {}
}
