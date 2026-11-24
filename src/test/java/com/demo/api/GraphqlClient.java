package com.demo.api;

import com.demo.config.Environment.LoginCredentials;
import com.demo.data.ApiUser;
import com.demo.data.NewUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Authenticated client for the app's {@code /graphql} endpoint. */
public final class GraphqlClient {

  /** User fields selected by every operation; the password is never requested. */
  private static final String USER_FIELDS = "id name email username";

  /** GraphQL documents for the operations the app's schema defines. */
  public static final class Documents {
    private Documents() {}

    public static final String USERS = "query Users { users { " + USER_FIELDS + " } }";
    public static final String USER =
        "query User($id: Int!) { user(id: $id) { " + USER_FIELDS + " } }";
    public static final String SEARCH_USERS =
        "query SearchUsers($name: String, $email: String) { searchUsers(name: $name, email: $email) { "
            + USER_FIELDS
            + " } }";
    public static final String USER_COUNT_WITH_IDS = "query UserCount { userCount users { id } }";
    public static final String CREATE_USER =
        "mutation CreateUser($name: String!, $email: String!, $username: String!, $password: String!) {"
            + " createUser(name: $name, email: $email, username: $username, password: $password) { "
            + USER_FIELDS
            + " } }";
    public static final String BULK_ADD_USERS =
        "mutation BulkAddUsers($users: [NewUserInput!]!) { bulkAddUsers(users: $users) { "
            + USER_FIELDS
            + " } }";
    public static final String UPDATE_USER =
        "mutation UpdateUser($id: Int!, $name: String, $email: String, $username: String, $password: String) {"
            + " updateUser(id: $id, name: $name, email: $email, username: $username, password: $password) { "
            + USER_FIELDS
            + " } }";
    public static final String DELETE_USER =
        "mutation DeleteUser($id: Int!) { deleteUser(id: $id) }";
  }

  /** One entry of a GraphQL {@code errors} array. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphqlError(String message, Extensions extensions) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Extensions(String code) {}

    /** The error code, or null when the error has none. */
    public String code() {
      return extensions == null ? null : extensions.code();
    }
  }

  /**
   * A GraphQL response with its HTTP status, before the caller decides whether errors are expected.
   *
   * @param data the {@code data} field: Java null when the response has none, a {@code NullNode}
   *     when it is JSON null
   */
  public record GraphqlResult(int status, JsonNode data, List<GraphqlError> errors) {}

  /** The count and IDs read in a single operation, so both describe the same data. */
  public record UserCountWithIds(int userCount, List<Integer> ids) {}

  private static final TypeReference<List<ApiUser>> USER_LIST = new TypeReference<>() {};
  private static final TypeReference<List<GraphqlError>> ERROR_LIST = new TypeReference<>() {};

  private final String apiKey;

  private GraphqlClient(String apiKey) {
    this.apiKey = apiKey;
  }

  /** Logs in through the REST API and returns a client that sends the resulting API key. */
  @Step("Log in to the GraphQL API")
  public static GraphqlClient login(LoginCredentials credentials) {
    return new GraphqlClient(UsersApiClient.requestApiKey(credentials));
  }

  /**
   * Sends an operation and validates only the response envelope, so callers can assert on errors.
   */
  public GraphqlResult execute(String document, Map<String, ?> variables) {
    return executeGraphql(document, variables, apiKey);
  }

  /**
   * Sends an operation with an optional API key (null sends none) and validates the response
   * envelope. Used directly by tests that must call the endpoint without the client's key.
   */
  public static GraphqlResult executeGraphql(
      String document, Map<String, ?> variables, String apiKey) {
    // The endpoint does not check the key today; sending it keeps the client working if it starts.
    Response response =
        (apiKey == null ? ApiSpecs.request() : ApiSpecs.request(apiKey))
            .body(Map.of("query", document, "variables", variables))
            .post("/graphql");
    String body = response.asString();
    Json.assertMatchesSchema(body, Schemas.GRAPHQL_RESPONSE, "GraphQL response");
    JsonNode root = Json.tree(body);
    List<GraphqlError> errors =
        root.has("errors") ? Json.convert(root.get("errors"), ERROR_LIST) : List.of();
    return new GraphqlResult(response.statusCode(), root.get("data"), errors);
  }

  /** Returns every user. */
  public List<ApiUser> listUsers() {
    return fetchField(Documents.USERS, "users", Schemas.API_USERS, USER_LIST, Map.of());
  }

  /** Returns the user with the given ID, or null when none exists. */
  public ApiUser getUser(int id) {
    return fetchNullableUser(Documents.USER, "user", Map.of("id", id));
  }

  /** Searches users by optional (nullable), case-insensitive name and email fragments. */
  public List<ApiUser> searchUsers(String name, String email) {
    Map<String, Object> variables = new HashMap<>();
    if (name != null) {
      variables.put("name", name);
    }
    if (email != null) {
      variables.put("email", email);
    }
    return fetchField(
        Documents.SEARCH_USERS, "searchUsers", Schemas.API_USERS, USER_LIST, variables);
  }

  /** Reads {@code userCount} and the user IDs in a single operation. */
  public UserCountWithIds getUserCountWithIds() {
    JsonNode data = requireData(execute(Documents.USER_COUNT_WITH_IDS, Map.of()), "userCount");
    List<Integer> ids =
        data.get("users").findValuesAsText("id").stream().map(Integer::valueOf).toList();
    return new UserCountWithIds(data.get("userCount").asInt(), ids);
  }

  /** Creates a user and returns it. */
  public ApiUser createUser(NewUser user) {
    return fetchField(
        Documents.CREATE_USER,
        "createUser",
        Schemas.API_USER,
        new TypeReference<>() {},
        variables(user));
  }

  /** Creates every user in one mutation and returns those added. */
  public List<ApiUser> bulkAddUsers(List<NewUser> users) {
    return fetchField(
        Documents.BULK_ADD_USERS,
        "bulkAddUsers",
        Schemas.API_USERS,
        USER_LIST,
        Map.of("users", users.stream().map(GraphqlClient::variables).toList()));
  }

  /** Updates the supplied fields and returns the user, or null when the ID does not exist. */
  public ApiUser updateUser(int id, Map<String, String> fields) {
    Map<String, Object> variables = new HashMap<>(fields);
    variables.put("id", id);
    return fetchNullableUser(Documents.UPDATE_USER, "updateUser", variables);
  }

  /** Deletes a user and returns whether the API reports that it did. */
  public boolean deleteUser(int id) {
    JsonNode deleted =
        requireData(execute(Documents.DELETE_USER, Map.of("id", id)), "deleteUser")
            .get("deleteUser");
    if (deleted == null || !deleted.isBoolean()) {
      throw new AssertionError("GraphQL deleteUser did not return a boolean: " + deleted);
    }
    return deleted.asBoolean();
  }

  /** The variables for a user payload. */
  public static Map<String, Object> variables(NewUser user) {
    return Map.of(
        "name", user.name(),
        "email", user.email(),
        "username", user.username(),
        "password", user.password());
  }

  /** Sends an operation that must succeed and returns one schema-validated field of its data. */
  private <T> T fetchField(
      String document,
      String field,
      String schemaFile,
      TypeReference<T> type,
      Map<String, ?> variables) {
    JsonNode value = requireData(execute(document, variables), field).get(field);
    Json.assertMatchesSchema(String.valueOf(value), schemaFile, "GraphQL " + field);
    return Json.convert(value, type);
  }

  private ApiUser fetchNullableUser(String document, String field, Map<String, ?> variables) {
    JsonNode value = requireData(execute(document, variables), field).get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    Json.assertMatchesSchema(value.toString(), Schemas.API_USER, "GraphQL " + field);
    return Json.convert(value, ApiUser.class);
  }

  private static JsonNode requireData(GraphqlResult result, String field) {
    if (result.status() != 200 || !result.errors().isEmpty() || result.data() == null) {
      String messages =
          String.join("; ", result.errors().stream().map(GraphqlError::message).toList());
      throw new AssertionError(
          "GraphQL %s returned %d: %s"
              .formatted(field, result.status(), messages.isEmpty() ? "no data" : messages));
    }
    return result.data();
  }
}
