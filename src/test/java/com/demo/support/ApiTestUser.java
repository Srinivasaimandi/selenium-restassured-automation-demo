package com.demo.support;

import com.demo.api.UsersApiClient;
import com.demo.data.NewUser;
import java.util.List;

/**
 * A user created through the API for one test. Closing it deletes the user by its unique email,
 * which tolerates the test having already deleted it. Use it in try-with-resources.
 */
public final class ApiTestUser implements AutoCloseable {

  private final NewUser data;
  private final int id;
  private final UsersApiClient usersApi;

  ApiTestUser(NewUser data, int id, UsersApiClient usersApi) {
    this.data = data;
    this.id = id;
    this.usersApi = usersApi;
  }

  public int id() {
    return id;
  }

  public String name() {
    return data.name();
  }

  public String email() {
    return data.email();
  }

  public String username() {
    return data.username();
  }

  @Override
  public void close() {
    usersApi.deleteUsersByEmail(List.of(data.email()));
  }
}
