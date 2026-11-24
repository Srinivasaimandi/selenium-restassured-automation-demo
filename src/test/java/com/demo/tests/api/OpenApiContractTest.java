package com.demo.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.api.Json;
import com.demo.api.OpenApiDocument;
import com.demo.api.Schemas;
import com.demo.api.UsersApiClient;
import com.demo.support.BaseTest;
import com.demo.support.KnownDefect;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Feature;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Checks the published OpenAPI document against the clients and schemas the suite relies on. */
@Feature("Users API OpenAPI contract")
public class OpenApiContractTest extends BaseTest {

  /**
   * The schema file the suite validates each documented JSON response with, keyed {@code METHOD
   * /path STATUS}. Every response the document gives a JSON schema must appear here.
   */
  private static final Map<String, String> RESPONSE_SCHEMAS =
      Map.of(
          "POST /login 200", Schemas.API_KEY,
          "POST /login 401", Schemas.API_ERROR,
          "PUT /users/reset-data 200", Schemas.API_MESSAGE,
          "PUT /users/reset-data 500", Schemas.API_ERROR);

  /**
   * Non-authentication statuses the suite has seen each operation return. 401 for a missing or
   * invalid API key is left out: the document declares that through its global security scheme.
   */
  private static final Map<String, List<String>> OBSERVED_STATUSES = observedStatuses();

  private OpenApiDocument document;

  @BeforeClass(alwaysRun = true)
  public void fetchDocument() {
    document = OpenApiDocument.fetch();
  }

  @Test(
      groups = {"api", "contract"},
      description = "documents exactly the operations the API client calls")
  public void documentsExactlyTheClientOperations() {
    assertThat(document.listOperations())
        .containsExactlyInAnyOrderElementsOf(UsersApiClient.OPERATIONS);
  }

  @Test(
      groups = {"api", "contract"},
      description = "documents POST /login as not needing an API key")
  public void loginNeedsNoApiKey() {
    JsonNode security = document.operation("POST /login").path("security");

    assertThat(security.isArray()).as("POST /login has a security list").isTrue();
    assertThat(security).isEmpty();
  }

  @Test(
      groups = {"api", "contract"},
      description = "documents the x-api-key header the clients authenticate with")
  public void documentsApiKeyHeader() {
    JsonNode scheme = document.json().path("components").path("securitySchemes").path("ApiKeyAuth");

    assertThat(scheme.path("type").asText()).isEqualTo("apiKey");
    assertThat(scheme.path("in").asText()).isEqualTo("header");
    assertThat(scheme.path("name").asText()).isEqualTo("x-api-key");
    assertThat(document.json().path("security"))
        .contains(Json.MAPPER.createObjectNode().set("ApiKeyAuth", Json.MAPPER.createArrayNode()));
  }

  @Test(
      groups = {"api", "contract"},
      description = "gives every documented JSON response a schema the tests validate with")
  public void everyDocumentedJsonResponseHasATestSchema() {
    List<String> documented = new ArrayList<>();
    for (String operation : document.listOperations()) {
      for (String status : document.responseStatuses(operation)) {
        if (!document.jsonResponseSchema(operation, status).isMissingNode()) {
          documented.add(operation + " " + status);
        }
      }
    }

    assertThat(documented).containsExactlyInAnyOrderElementsOf(RESPONSE_SCHEMAS.keySet());
  }

  @Test(
      groups = {"api", "contract"},
      description = "describes each documented response with the same fields as the test schema")
  public void documentedResponsesMatchTestSchemas() {
    RESPONSE_SCHEMAS.forEach((key, schemaFile) -> {
      String[] parts = key.split(" ");
      JsonNode documented = document.jsonResponseSchema(parts[0] + " " + parts[1], parts[2]);

      assertThat(documented.isMissingNode()).as("%s has no documented JSON schema", key).isFalse();
      assertThat(OpenApiDocument.compareWithTestSchema(documented, Json.schema(schemaFile)))
          .as(key)
          .isEmpty();
    });
  }

  @KnownDefect(
      "The document omits 204 for GET /users and GET /users/search, and 403 for DELETE /users/{id}")
  @Test(
      groups = {"api", "contract", "known-defect"},
      description = "documents every status the API returns")
  public void documentsEveryObservedStatus() {
    List<String> undocumented = new ArrayList<>();
    OBSERVED_STATUSES.forEach((operation, statuses) -> {
      List<String> documented = document.responseStatuses(operation);
      statuses.stream()
          .filter(status -> !documented.contains(status))
          .forEach(status -> undocumented.add(operation + " " + status));
    });

    assertThat(undocumented).isEmpty();
  }

  private static Map<String, List<String>> observedStatuses() {
    Map<String, List<String>> statuses = new LinkedHashMap<>();
    statuses.put("POST /login", List.of("200", "401"));
    statuses.put("POST /logout", List.of("204"));
    statuses.put("GET /users", List.of("200", "204"));
    statuses.put("POST /users", List.of("201", "400"));
    statuses.put("GET /users/search", List.of("200", "204"));
    statuses.put("GET /users/count", List.of("200"));
    statuses.put("PUT /users/reset-data", List.of("200"));
    statuses.put("POST /users/bulk", List.of("201", "400"));
    statuses.put("GET /users/{id}", List.of("200", "404"));
    statuses.put("PUT /users/{id}", List.of("200", "404"));
    statuses.put("DELETE /users/{id}", List.of("204", "403", "404"));
    return statuses;
  }
}
