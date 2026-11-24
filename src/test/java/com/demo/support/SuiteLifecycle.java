package com.demo.support;

import com.demo.config.Environment;
import com.demo.config.LocalApp;
import com.demo.ui.DriverFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Starts the bundled app before the suite and stops it afterwards, and counts failed tests so the
 * destructive tests can skip when anything before them failed.
 */
public class SuiteLifecycle implements ISuiteListener, ITestListener {

  private static final AtomicInteger FAILURES = new AtomicInteger();

  /** The number of tests that have failed so far in this run. */
  public static int failureCount() {
    return FAILURES.get();
  }

  @Override
  public void onStart(ISuite suite) {
    System.out.printf(
        "[suite] TEST_ENV=%s BASE_URL=%s browser=%s%n",
        Environment.testEnv(), Environment.baseUrl(), DriverFactory.browserName());
    LocalApp.startIfNeeded();
  }

  @Override
  public void onFinish(ISuite suite) {
    LocalApp.stop();
  }

  @Override
  public void onTestFailure(ITestResult result) {
    FAILURES.incrementAndGet();
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    FAILURES.incrementAndGet();
  }
}
