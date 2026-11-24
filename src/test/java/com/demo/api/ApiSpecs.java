package com.demo.api;

import static io.restassured.RestAssured.given;

import com.demo.config.Environment;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/** Shared REST Assured request setup for the app under test. */
public final class ApiSpecs {

  private ApiSpecs() {}

  /** An unauthenticated JSON request to the app, recorded in the Allure report. */
  public static RequestSpecification request() {
    return given()
        .spec(
            new RequestSpecBuilder()
                .setBaseUri(Environment.baseUrl())
                .setContentType(ContentType.JSON)
                .addFilter(new AllureRestAssured())
                .build());
  }

  /** A JSON request that sends the given API key. */
  public static RequestSpecification request(String apiKey) {
    return request().header("x-api-key", apiKey);
  }
}
