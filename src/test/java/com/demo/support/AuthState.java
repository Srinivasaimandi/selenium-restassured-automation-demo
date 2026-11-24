package com.demo.support;

import com.demo.api.UsersApiClient;
import com.demo.config.Environment;

/**
 * The shared signed-in browser state, like Playwright's saved {@code storageState}: the API token
 * the app keeps in {@code localStorage}. The setup test saves it after a real UI login; tests that
 * start signed in reuse it instead of logging in through the form.
 */
public final class AuthState {

  private static volatile String apiToken;

  private AuthState() {}

  /** Saves the token captured by the setup test's UI login. */
  public static void save(String token) {
    apiToken = token;
  }

  /**
   * The shared token. When the setup test did not run (for example, one class run on its own), logs
   * in through the API once instead. Skips the test without login credentials.
   */
  public static synchronized String apiToken() {
    if (apiToken == null) {
      apiToken =
          UsersApiClient.requestApiKey(
              Environment.requireLoginCredentials("authenticated users tests"));
    }
    return apiToken;
  }
}
