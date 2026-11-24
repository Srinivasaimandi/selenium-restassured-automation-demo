package com.demo.data;

import java.util.UUID;

/** A user payload for create requests and the add-user form. */
public record NewUser(String name, String email, String username, String password) {

  /** Creates distinct user data from this template for an isolated test. */
  public NewUser unique() {
    String uniqueId = UUID.randomUUID().toString();
    return new NewUser(
        "Selenium " + uniqueId.substring(0, 8),
        uniqueId + "@example.test",
        "se_" + uniqueId.replace("-", ""),
        password);
  }

  public NewUser withName(String value) {
    return new NewUser(value, email, username, password);
  }

  public NewUser withEmail(String value) {
    return new NewUser(name, value, username, password);
  }

  public NewUser withUsername(String value) {
    return new NewUser(name, email, value, password);
  }

  public NewUser withPassword(String value) {
    return new NewUser(name, email, username, value);
  }
}
