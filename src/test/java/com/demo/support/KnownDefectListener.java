package com.demo.support;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/** Inverts the result of tests annotated with {@link KnownDefect}. */
public class KnownDefectListener implements IInvokedMethodListener {

  @Override
  public void afterInvocation(IInvokedMethod method, ITestResult result) {
    if (!method.isTestMethod()) {
      return;
    }
    KnownDefect defect =
        method
            .getTestMethod()
            .getConstructorOrMethod()
            .getMethod()
            .getAnnotation(KnownDefect.class);
    if (defect == null) {
      return;
    }
    switch (result.getStatus()) {
      case ITestResult.FAILURE -> {
        System.out.printf(
            "[known-defect] %s failed as expected: %s%n  cause: %s%n",
            result.getName(), defect.value(), result.getThrowable());
        result.setStatus(ITestResult.SUCCESS);
        result.setThrowable(null);
      }
      case ITestResult.SUCCESS -> {
        result.setStatus(ITestResult.FAILURE);
        result.setThrowable(
            new AssertionError(
                "Expected to fail because of a known app defect, but passed. If the defect is"
                    + " fixed, remove @KnownDefect. Defect: "
                    + defect.value()));
      }
      default -> {
        // Skipped tests stay skipped.
      }
    }
  }
}
