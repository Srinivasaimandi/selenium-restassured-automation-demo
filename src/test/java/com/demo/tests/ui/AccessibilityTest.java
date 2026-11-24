package com.demo.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.data.TestData;
import com.demo.support.BaseUiTest;
import com.demo.support.KnownDefect;
import com.demo.ui.pages.UsersPage;
import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import io.qameta.allure.Feature;
import java.util.List;
import org.testng.annotations.Test;

/** Automated WCAG 2.1 A/AA scans of each screen and dialog, with axe-core. */
@Feature("Accessibility")
public class AccessibilityTest extends BaseUiTest {

  @Test(groups = "a11y", description = "login page has no detectable WCAG violations")
  public void loginPage_hasNoViolations() {
    loginPage().goTo();
    expect().visible(loginPage().heading);

    Results results = axe().analyze(driver());

    assertNoViolations(results);
  }

  @Test(groups = "a11y", description = "users page has no detectable WCAG violations")
  public void usersPage_hasNoViolations() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());

    // The protected-user lock icon is scanned on its own below, as a known app defect.
    Results results = axe().exclude(".protected-user").analyze(driver());

    assertNoViolations(results);
  }

  @Test(groups = "a11y", description = "add-user dialog has no detectable WCAG violations")
  public void addUserDialog_hasNoViolations() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    usersPage.openAddUserDialog();
    expect().visible(usersPage.addUserDialog);

    Results results = axe().include("[role=\"dialog\"]").analyze(driver());

    assertNoViolations(results);
  }

  @KnownDefect(
      "The lock icon is a <span aria-label=\"Protected user\"> with no role; aria-label is"
          + " prohibited on a role-less span (axe aria-prohibited-attr, WCAG 4.1.2)")
  @Test(
      groups = {"a11y", "known-defect"},
      description = "protected-user lock icon has no detectable WCAG violations")
  public void protectedUserIcon_hasNoViolations() {
    UsersPage usersPage = openAuthenticatedUsersPage();
    expect().visible(usersPage.userRow(TestData.JANE.name()).locator());

    Results results = axe().include(".protected-user").analyze(driver());

    assertNoViolations(results);
  }

  private static void assertNoViolations(Results results) {
    List<String> violations = results.getViolations().stream().map(Rule::getId).toList();
    assertThat(violations)
        .as("axe violations: %s", results.getViolations().stream().map(Rule::getHelp).toList())
        .isEmpty();
  }
}
