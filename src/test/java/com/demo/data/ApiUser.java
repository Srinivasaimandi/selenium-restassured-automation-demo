package com.demo.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A user record as the API returns it; the password is never read. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiUser(int id, String name, String email, String username) {

  public ApiUser withName(String value) {
    return new ApiUser(id, value, email, username);
  }
}
