package com.demo.ui.pages;

import com.demo.config.Environment;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Shared page actions. Locators are {@link By} values, resolved when an action or check runs. */
abstract class BasePage {

  protected final WebDriver driver;
  private final WebDriverWait wait;

  BasePage(WebDriver driver) {
    this.driver = driver;
    this.wait = new WebDriverWait(driver, Duration.ofSeconds(5));
  }

  /** Opens a path relative to {@code BASE_URL}. */
  protected void open(String path) {
    driver.get(Environment.baseUrl() + path);
  }

  /** Clicks an element once it is visible and enabled. */
  protected void click(By locator) {
    wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
  }

  /**
   * Replaces a field's value by typing, so the page receives input events like a user's. An empty
   * value is typed as a character and a backspace, because clearing alone fires no input event.
   */
  protected void fill(By locator, String value) {
    WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    field.clear();
    if (value.isEmpty()) {
      field.sendKeys(" ", Keys.BACK_SPACE);
    } else {
      field.sendKeys(value);
    }
  }
}
