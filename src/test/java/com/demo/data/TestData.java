package com.demo.data;

/** Stable reference data shared by UI and API tests. */
public final class TestData {

  private TestData() {}

  /** A user the local app seeds, without the password. */
  public record SeededUser(String name, String email, String username) {}

  /** Seeded user present in the local app's data. */
  public static final SeededUser JANE =
      new SeededUser("Jane Smith", "janesmith@example.com", "janesmith");

  /** Seeded user the API refuses to delete. */
  public static final int DELETE_PROTECTED_USER_ID = 3;

  /** An ID no user has. */
  public static final int MISSING_USER_ID = 999_999_999;

  /** Template for users that tests create; call {@link NewUser#unique()} before using it. */
  public static final NewUser NEW_USER =
      new NewUser("Test User", "test.user@example.com", "testuser", "TestPassword123!");

  /** Search terms with known results against the seeded data. */
  public static final class Searches {
    private Searches() {}

    /**
     * Lower-case fragment of seeded names (Jane Smith, Srinivasa Rao Imandi) that no other seeded
     * field contains.
     */
    public static final String JANE_BY_PARTIAL_NAME = "an";

    public static final String JANE_BY_EMAIL_CASE_INSENSITIVE = "JANESMITH@EXAMPLE.COM";
    public static final String NO_RESULTS = "no-such-user-998";
  }

  /**
   * Users served by mocked API responses. IDs are far above the seeded ones, so a request that
   * escapes its mock hits no real record. ID 0 is the admin account the screen never lists.
   */
  public static final class MockUsers {
    private MockUsers() {}

    public static final ApiUser ADMIN =
        new ApiUser(0, "Admin Account", "admin.account@example.test", "admin");
    public static final ApiUser ALICE =
        new ApiUser(901, "Mock Alice", "mock.alice@example.test", "mockalice");
    public static final ApiUser BOB =
        new ApiUser(902, "Mock Bob", "mock.bob@example.test", "mockbob");
  }
}
