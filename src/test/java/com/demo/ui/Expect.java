package com.demo.ui;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;

/**
 * Assertions that wait for the page to reach the expected state, like Playwright's web-first {@code
 * expect}. Each one polls until its condition holds or the timeout passes, then fails with the
 * expected and last observed values.
 */
public final class Expect {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Duration POLL = Duration.ofMillis(100);

  private final WebDriver driver;

  public Expect(WebDriver driver) {
    this.driver = driver;
  }

  public void visible(By locator) {
    await("%s to be visible".formatted(locator), d -> isVisible(locator), Boolean.TRUE::equals);
  }

  /** Passes when no matching element is displayed, including when none exists. */
  public void hidden(By locator) {
    await("%s to be hidden".formatted(locator), d -> isVisible(locator), Boolean.FALSE::equals);
  }

  public void count(By locator, int expected) {
    await(
        "%s to match %d element(s)".formatted(locator, expected),
        d -> d.findElements(locator).size(),
        actual -> actual == expected);
  }

  /** Passes when the matching elements' texts equal the expected list, in order. */
  public void texts(By locator, List<String> expected) {
    await(
        "%s to have texts %s".formatted(locator, expected), d -> texts(locator), expected::equals);
  }

  /** Passes when every matching element's text matches the pattern; there must be at least one. */
  public void allTextsMatch(By locator, Pattern pattern) {
    await(
        "every %s text to match /%s/".formatted(locator, pattern),
        d -> texts(locator),
        actual -> !actual.isEmpty() && actual.stream().allMatch(t -> pattern.matcher(t).find()));
  }

  public void text(By locator, String expected) {
    await(
        "%s to have text \"%s\"".formatted(locator, expected),
        d -> d.findElement(locator).getText().trim(),
        expected::equals);
  }

  public void containsText(By locator, String expected) {
    await(
        "%s to contain text \"%s\"".formatted(locator, expected),
        d -> d.findElement(locator).getText(),
        actual -> actual.contains(expected));
  }

  public void value(By locator, String expected) {
    await(
        "%s to have value \"%s\"".formatted(locator, expected),
        d -> d.findElement(locator).getDomProperty("value"),
        expected::equals);
  }

  public void enabled(By locator) {
    await("%s to be enabled".formatted(locator), d -> d.findElement(locator).isEnabled(), b -> b);
  }

  public void disabled(By locator) {
    await("%s to be disabled".formatted(locator), d -> d.findElement(locator).isEnabled(), b -> !b);
  }

  public void focused(By locator) {
    await(
        "%s to be focused".formatted(locator),
        d -> d.switchTo().activeElement().equals(d.findElement(locator)),
        b -> b);
  }

  /** Passes when the DOM attribute (as written in the HTML) matches the pattern. */
  public void attribute(By locator, String name, Pattern pattern) {
    await(
        "%s attribute %s to match /%s/".formatted(locator, name, pattern),
        d -> d.findElement(locator).getDomAttribute(name),
        actual -> actual != null && pattern.matcher(actual).find());
  }

  public void urlMatches(Pattern pattern) {
    await(
        "the URL to match /%s/".formatted(pattern),
        WebDriver::getCurrentUrl,
        url -> pattern.matcher(url).find());
  }

  public void title(String expected) {
    await("the title to be \"%s\"".formatted(expected), WebDriver::getTitle, expected::equals);
  }

  /** Polls any value from the browser until it satisfies the condition. */
  public <T> void poll(String description, Function<WebDriver, T> read, Predicate<T> condition) {
    await(description, read, condition);
  }

  private boolean isVisible(By locator) {
    return driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
  }

  private List<String> texts(By locator) {
    return driver.findElements(locator).stream().map(e -> e.getText().trim()).toList();
  }

  private <T> void await(String expectation, Function<WebDriver, T> read, Predicate<T> condition) {
    Object[] last = {"<not read>"};
    try {
      new FluentWait<>(driver)
          .withTimeout(TIMEOUT)
          .pollingEvery(POLL)
          .ignoring(NoSuchElementException.class)
          .ignoring(StaleElementReferenceException.class)
          .until(d -> {
            T value = read.apply(d);
            last[0] = value;
            return condition.test(value) ? Boolean.TRUE : null;
          });
    } catch (TimeoutException e) {
      throw new AssertionError(
          "Timed out after %ss waiting for %s. Last value: %s"
              .formatted(TIMEOUT.toSeconds(), expectation, Objects.toString(last[0])),
          e);
    }
  }
}
