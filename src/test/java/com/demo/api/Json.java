package com.demo.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.module.jsv.JsonSchemaValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.hamcrest.MatcherAssert;

/** JSON parsing and JSON Schema validation shared by the API clients and tests. */
public final class Json {

  /** Reads only the fields a record declares, like a Zod object schema strips unknown keys. */
  public static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private Json() {}

  /** Parses JSON text into a tree. */
  public static JsonNode tree(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new AssertionError("Response is not valid JSON: " + abbreviate(json), e);
    }
  }

  /** Converts a JSON tree into a typed value. */
  public static <T> T convert(JsonNode node, TypeReference<T> type) {
    return MAPPER.convertValue(node, type);
  }

  /** Converts a JSON tree into a typed value. */
  public static <T> T convert(JsonNode node, Class<T> type) {
    return MAPPER.convertValue(node, type);
  }

  /** Serialises a value as JSON text. */
  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not serialise " + value, e);
    }
  }

  /**
   * Fails with the validator's messages when JSON text does not match a schema in {@code
   * src/test/resources/schemas}.
   */
  public static void assertMatchesSchema(String json, String schemaFile, String context) {
    MatcherAssert.assertThat(
        context + " does not match schemas/" + schemaFile,
        json,
        JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/" + schemaFile));
  }

  /** Reads one of the schema files as a tree, for comparing it with the OpenAPI document. */
  public static JsonNode schema(String schemaFile) {
    try (InputStream in = Json.class.getResourceAsStream("/schemas/" + schemaFile)) {
      if (in == null) {
        throw new IllegalArgumentException("No schema file schemas/" + schemaFile);
      }
      return MAPPER.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String abbreviate(String text) {
    return text.length() > 500 ? text.substring(0, 500) + "..." : text;
  }
}
