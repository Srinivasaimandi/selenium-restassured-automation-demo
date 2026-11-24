package com.demo.ui.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** Locators and actions for the login form on the home page. */
public class LoginPage extends BasePage {

  public final By heading = By.xpath("//h2[normalize-space()='Login']");
  public final By usernameInput = By.cssSelector("#login-form input[placeholder='Enter Username']");
  public final By passwordInput = By.cssSelector("#login-form input[placeholder='Enter Password']");
  public final By submitButton =
      By.xpath("//form[@id='login-form']//button[normalize-space()='Login']");

  public LoginPage(WebDriver driver) {
    super(driver);
  }

  /** Opens the home page, which holds the login form. */
  @Step("Open the login page")
  public void goTo() {
    open("/");
  }

  /** Submits the login form with the given credentials. */
  @Step("Log in as {username}")
  public void login(String username, String password) {
    fill(usernameInput, username);
    fill(passwordInput, password);
    click(submitButton);
  }

  public void submit() {
    click(submitButton);
  }

  public void fillUsername(String username) {
    fill(usernameInput, username);
  }
}
