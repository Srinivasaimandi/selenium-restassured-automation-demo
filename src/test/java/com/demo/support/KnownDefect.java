package com.demo.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test of expected behaviour the app does not implement yet, like Playwright's {@code
 * test.fail}. {@link KnownDefectListener} reports the test as passed while it fails, and fails it
 * once it starts passing, so a fixed defect is noticed and the annotation removed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface KnownDefect {

  /** The app defect that makes this test fail. */
  String value();
}
