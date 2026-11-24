package com.demo.ui.pages;

import com.demo.data.NewUser;
import io.qameta.allure.Step;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Locators and actions for the users list, its row actions, and its dialogs. */
public class UsersPage extends BasePage {

  private static final String TABLE = "//table[@id='users-table']";
  private static final String BODY_ROWS = TABLE + "/tbody/tr";

  public final By heading = By.xpath("//h1[normalize-space()='Users List']");
  public final By searchInput = By.id("search-input");
  public final By addUserButton = By.cssSelector("button[aria-label='Add user']");
  public final By refreshUsersButton = By.cssSelector("button[aria-label='Refresh users']");
  public final By resetDataButton = By.cssSelector("button[aria-label='Reset data']");
  public final By logoutLink = By.xpath("//a[normalize-space()='Logout']");
  public final By usersTable = By.xpath(TABLE);
  public final By columnHeaders = By.xpath(TABLE + "/thead/tr/th");
  public final By userRows = By.xpath(BODY_ROWS);
  public final By fetchFailedMessage = statusMessage("Failed to fetch users.");
  public final By noUsersMessage = statusMessage("No users found.");

  public final By addUserDialog = By.cssSelector("#add-user-modal [role='dialog']");
  public final By addUserValidationMessage = addUserError("Please fill out all fields.");
  public final By addUserNameInput = By.id("add-name");
  public final By addUserEmailInput = By.id("add-email");
  public final By addUserUsernameInput = By.id("add-username");
  public final By addUserPasswordInput = By.id("add-password");
  public final By saveUserButton = By.id("add-user-save");
  public final By cancelAddUserButton = By.id("add-user-cancel");

  public final By deleteUserDialog = By.cssSelector("#delete-user-modal [role='alertdialog']");
  public final By deleteUserMessage = By.id("delete-user-message");
  public final By confirmDeleteButton = By.id("delete-user-confirm");
  public final By cancelDeleteButton = By.id("delete-user-cancel");

  public UsersPage(WebDriver driver) {
    super(driver);
  }

  /** Opens the users page directly. */
  public void goTo() {
    open("/usersPage.html");
  }

  /** Locates a message in the page's status banner. */
  public static By statusMessage(String message) {
    return By.xpath("//div[@id='error-msg' and normalize-space()=" + XPath.literal(message) + "]");
  }

  /** Locates an error message shown inside the add-user dialog. */
  public static By addUserError(String message) {
    return By.xpath(
        "//div[@id='add-user-error' and normalize-space()=" + XPath.literal(message) + "]");
  }

  @Step("Search users for \"{query}\"")
  public void searchFor(String query) {
    fill(searchInput, query);
  }

  @Step("Open the add-user dialog")
  public void openAddUserDialog() {
    click(addUserButton);
  }

  /** Enters a complete user payload into the add-user dialog. */
  @Step("Fill the add-user form")
  public void fillAddUserForm(NewUser user) {
    fill(addUserNameInput, user.name());
    fill(addUserEmailInput, user.email());
    fill(addUserUsernameInput, user.username());
    fill(addUserPasswordInput, user.password());
  }

  public void saveNewUser() {
    click(saveUserButton);
  }

  /** Closes the add-user dialog without saving its draft. */
  public void cancelAddUser() {
    click(cancelAddUserButton);
  }

  public void refreshUsers() {
    click(refreshUsersButton);
  }

  public void resetData() {
    click(resetDataButton);
  }

  /** Clicks one of a row's action buttons. */
  public void clickRowButton(Row row, String name) {
    click(row.button(name));
  }

  /** Replaces the value of one of a row's inline-edit fields (0 name, 1 email, 2 username). */
  public void fillRowEditField(Row row, int index, String value) {
    fill(row.editField(index), value);
  }

  /** Deletes a row's user: clicks its delete button, then confirms in the delete dialog. */
  @Step("Delete a user row")
  public void deleteRow(Row row) {
    clickRowButton(row, Row.DELETE);
    click(confirmDeleteButton);
  }

  public void cancelDelete() {
    click(cancelDeleteButton);
  }

  /** Ends the session through the page's logout link. */
  @Step("Log out")
  public void logout() {
    click(logoutLink);
  }

  /** Reads the login token the page stores for API calls, or null when signed out. */
  public String getStoredApiToken() {
    return (String)
        ((JavascriptExecutor) driver).executeScript("return localStorage.getItem('x-api-token');");
  }

  /**
   * Locates every body cell under the column with the given header text. The header's position is
   * resolved at runtime, so lookups stay correct if columns are reordered.
   */
  public By columnCells(String header) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.presenceOfElementLocated(columnHeaders));
    List<String> headers =
        driver.findElements(columnHeaders).stream()
            .map(e -> e.getDomProperty("textContent").trim())
            .toList();
    int index = headers.indexOf(header);
    if (index == -1) {
      throw new IllegalArgumentException(
          "Users table has no \"%s\" column. Found: %s".formatted(header, headers));
    }
    return By.xpath(BODY_ROWS + "/td[" + (index + 1) + "]");
  }

  /** Locates the table row containing the given text, such as a user's name. */
  public Row userRow(String text) {
    return new Row(BODY_ROWS + "[contains(normalize-space(.), " + XPath.literal(text) + ")]");
  }

  /**
   * Locates a row by its server-assigned user ID. The table does not show IDs (its first column is
   * a row number), so the row is matched by the {@code data-user-id} the app puts on the row's
   * action buttons. This keeps working while the row is in edit mode.
   */
  public Row userRowById(int id) {
    return new Row(BODY_ROWS + "[.//button[@data-user-id='" + id + "']]");
  }

  /** Reads the server-assigned user ID of a row that has edit and delete buttons. */
  public int rowUserId(Row row) {
    WebElement edit =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.presenceOfElementLocated(row.button(Row.EDIT)));
    String id = edit.getDomAttribute("data-user-id");
    if (id == null) {
      throw new IllegalStateException(
          "The row has no data-user-id; protected users have no action buttons.");
    }
    return Integer.parseInt(id);
  }
}
