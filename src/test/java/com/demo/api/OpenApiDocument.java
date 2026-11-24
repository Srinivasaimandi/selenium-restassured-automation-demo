package com.demo.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The OpenAPI 3 document the app publishes, with the lookups the contract tests need. */
public final class OpenApiDocument {

  /** HTTP methods that can appear as keys under an OpenAPI path item. */
  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "patch", "head", "options");

  /** The object's own lines are indented, so the first unindented closing brace ends it. */
  private static final Pattern SWAGGER_OPTIONS =
      Pattern.compile("var options = (\\{[\\s\\S]*?\\n\\});?\\n");

  private final JsonNode document;

  private OpenApiDocument(JsonNode document) {
    this.document = document;
  }

  /**
   * Fetches the document the app publishes through Swagger UI. The app has no raw JSON endpoint;
   * Swagger UI embeds the document in {@code swagger-ui-init.js} as {@code var options = {
   * "swaggerDoc": {...} }}, so it is read from there.
   */
  public static OpenApiDocument fetch() {
    Response response = ApiSpecs.request().get("/api-docs/swagger-ui-init.js");
    ApiResponses.assertStatus(response, 200);
    Matcher match = SWAGGER_OPTIONS.matcher(response.asString());
    if (!match.find()) {
      throw new AssertionError(
          "Could not find the embedded Swagger options in /api-docs/swagger-ui-init.js");
    }
    JsonNode document = Json.tree(match.group(1)).path("swaggerDoc");
    if (!document.path("openapi").asText().startsWith("3.")) {
      throw new AssertionError("Expected an OpenAPI 3 document, got: " + document.path("openapi"));
    }
    return new OpenApiDocument(document);
  }

  /** The raw document. */
  public JsonNode json() {
    return document;
  }

  /** Lists every documented operation as {@code METHOD /path}. */
  public List<String> listOperations() {
    List<String> operations = new ArrayList<>();
    document
        .path("paths")
        .properties()
        .forEach(path ->
            path.getValue()
                .fieldNames()
                .forEachRemaining(method -> {
                  if (HTTP_METHODS.contains(method)) {
                    operations.add(method.toUpperCase(Locale.ROOT) + " " + path.getKey());
                  }
                }));
    return operations;
  }

  /** Looks up a documented operation by its {@code METHOD /path} key. */
  public JsonNode operation(String operation) {
    String[] parts = operation.split(" ");
    JsonNode found = document.path("paths").path(parts[1]).path(parts[0].toLowerCase(Locale.ROOT));
    if (found.isMissingNode()) {
      throw new AssertionError("The OpenAPI document has no " + operation + " operation");
    }
    return found;
  }

  /** The documented response statuses of an operation. */
  public List<String> responseStatuses(String operation) {
    List<String> statuses = new ArrayList<>();
    operation(operation).path("responses").fieldNames().forEachRemaining(statuses::add);
    return statuses;
  }

  /** The documented JSON schema of one response, or a missing node when it has none. */
  public JsonNode jsonResponseSchema(String operation, String status) {
    return operation(operation)
        .path("responses")
        .path(status)
        .path("content")
        .path("application/json")
        .path("schema");
  }

  /**
   * Compares a documented response schema with the schema file the tests validate with. Returns
   * readable differences; an empty list means the two agree. Every documented property must exist
   * in the test schema with the same type, and every property the test schema requires must be
   * documented.
   */
  public static List<String> compareWithTestSchema(JsonNode documented, JsonNode testSchema) {
    List<String> differences = new ArrayList<>();
    String documentedType = documented.path("type").asText(null);
    String testType = testSchema.path("type").asText(null);
    if (documentedType == null ? testType != null : !documentedType.equals(testType)) {
      differences.add(
          "type is \"%s\" in the document, \"%s\" in the test".formatted(documentedType, testType));
    }

    JsonNode documentedProperties = documented.path("properties");
    JsonNode testProperties = testSchema.path("properties");
    documentedProperties
        .properties()
        .forEach(property -> {
          JsonNode testProperty = testProperties.get(property.getKey());
          if (testProperty == null) {
            differences.add(
                "documented property \"%s\" is not in the test schema"
                    .formatted(property.getKey()));
          } else if (!property.getValue().path("type").equals(testProperty.path("type"))) {
            differences.add(
                "property \"%s\" is \"%s\" in the document, \"%s\" in the test"
                    .formatted(
                        property.getKey(),
                        property.getValue().path("type").asText(),
                        testProperty.path("type").asText()));
          }
        });

    for (JsonNode required : testSchema.path("required")) {
      if (!documentedProperties.has(required.asText())) {
        differences.add(
            "required test property \"%s\" is not documented".formatted(required.asText()));
      }
    }
    return differences;
  }
}
