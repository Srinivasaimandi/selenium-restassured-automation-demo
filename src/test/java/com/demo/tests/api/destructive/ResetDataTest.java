package com.demo.tests.api.destructive;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.config.Environment;
import com.demo.data.ApiUser;
import com.demo.data.TestData;
import com.demo.support.ApiTestUser;
import com.demo.support.BaseTest;
import com.demo.support.SuiteLifecycle;
import io.qameta.allure.Feature;
import java.util.List;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Restoring the seeded data deletes every user added since, including those other tests are using,
 * so this class runs in the last section of the suite, alone, and skips if anything before it
 * failed (like Playwright's dependent {@code api-destructive} project).
 */
@Feature("PUT /users/reset-data")
public class ResetDataTest extends BaseTest {

  @BeforeClass(alwaysRun = true)
  public void skipAfterEarlierFailures() {
    int failures = SuiteLifecycle.failureCount();
    if (failures > 0) {
      throw new SkipException(
          failures + " earlier test(s) failed, so the destructive tests are skipped");
    }
  }

  @Test(
      groups = {"api", "test-data", "destructive"},
      description = "restores the seeded users and removes added ones")
  public void restoresSeededUsers() {
    Environment.assertTestDataTargetIsSafe();
    try (ApiTestUser added = createApiTestUser()) {
      assertThat(usersApi().resetData()).isEqualTo("Data has been reset from backup.");

      assertThat(usersApi().getUserStatus(added.id())).isEqualTo(404);
      List<ApiUser> jane = usersApi().searchUsers(null, TestData.JANE.email());
      assertThat(jane).singleElement().extracting(ApiUser::name).isEqualTo(TestData.JANE.name());
      assertThat(usersApi().getUserStatus(TestData.DELETE_PROTECTED_USER_ID)).isEqualTo(200);
    }
  }
}
