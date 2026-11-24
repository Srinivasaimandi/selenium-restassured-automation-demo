package com.demo.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.restassured.response.Response;

/** Status checks and schema-validated parsing for REST Assured responses. */
public final class ApiResponses {

  private ApiResponses() {}

  /** Fails with the response body when the status is not the expected one. */
  public static void assertStatus(Response response, int expectedStatus) {
    if (response.statusCode() != expectedStatus) {
      throw new AssertionError(
          "Expected HTTP %d but got %d. Body: %s"
              .formatted(
                  expectedStatus, response.statusCode(), Json.abbreviate(response.asString())));
    }
  }

  /**
   * Checks the expected status, validates the JSON body with a schema, and parses it.
   *
   * @param schemaFile a file in {@code src/test/resources/schemas}
   */
  public static <T> T parse(
      Response response, int expectedStatus, String schemaFile, Class<T> type) {
    assertStatus(response, expectedStatus);
    String body = response.asString();
    Json.assertMatchesSchema(body, schemaFile, "HTTP " + expectedStatus + " response");
    return Json.convert(Json.tree(body), type);
  }

  /** Like {@link #parse(Response, int, String, Class)}, for generic types such as lists. */
  public static <T> T parse(
      Response response, int expectedStatus, String schemaFile, TypeReference<T> type) {
    assertStatus(response, expectedStatus);
    String body = response.asString();
    Json.assertMatchesSchema(body, schemaFile, "HTTP " + expectedStatus + " response");
    return Json.convert(Json.tree(body), type);
  }

  /** Parses an error response with the expected status. */
  public static ApiError parseError(Response response, int expectedStatus) {
    return parse(response, expectedStatus, Schemas.API_ERROR, ApiError.class);
  }
}
