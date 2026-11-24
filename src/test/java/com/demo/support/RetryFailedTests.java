package com.demo.support;

import com.demo.config.Environment;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.ITestAnnotation;

/**
 * Retries failed tests twice in CI and never locally (override with {@code -Dretries=N}), like
 * Playwright's {@code retries: isCI ? 2 : 0}. Tests marked {@link KnownDefect} are never retried.
 */
public class RetryFailedTests implements IAnnotationTransformer {

  private static final int MAX_RETRIES =
      Integer.parseInt(System.getProperty("retries", Environment.isCi() ? "2" : "0"));

  @Override
  public void transform(
      ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
    if (MAX_RETRIES > 0
        && (testMethod == null || !testMethod.isAnnotationPresent(KnownDefect.class))) {
      annotation.setRetryAnalyzer(Analyzer.class);
    }
  }

  /** Retries one test method up to {@link #MAX_RETRIES} times. */
  public static class Analyzer implements IRetryAnalyzer {
    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    public boolean retry(ITestResult result) {
      return attempts.incrementAndGet() <= MAX_RETRIES;
    }
  }
}
