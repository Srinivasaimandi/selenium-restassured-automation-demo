package com.demo.ui.pages;

import org.openqa.selenium.By;

/** A users-table row, with locators for the elements inside it. */
public record Row(String xpath) {

  public static final String EDIT = "Edit row";
  public static final String DELETE = "Delete row";
  public static final String SAVE = "Save changes";
  public static final String CANCEL = "Cancel editing";

  /** The row itself. */
  public By locator() {
    return By.xpath(xpath);
  }

  /** One of the row's action buttons, by its accessible name. */
  public By button(String name) {
    return By.xpath(xpath + "//button[@aria-label=" + XPath.literal(name) + "]");
  }

  /** The row's inline-edit fields, in column order: name, email, username. */
  public By editFields() {
    return By.xpath(xpath + "//input");
  }

  /** One inline-edit field, counting from 0. */
  public By editField(int index) {
    return By.xpath("(" + xpath + "//input)[" + (index + 1) + "]");
  }

  /** A cell of this row whose whole text equals the given text. */
  public By cell(String text) {
    return By.xpath(xpath + "/td[normalize-space()=" + XPath.literal(text) + "]");
  }
}
