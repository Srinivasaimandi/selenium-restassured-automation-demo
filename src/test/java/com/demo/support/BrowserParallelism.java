package com.demo.support;

import com.demo.ui.DriverFactory;
import java.util.List;
import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

/**
 * Runs the UI section of testng.xml one test at a time when the browser allows only one session
 * (Safari). Registered through {@code META-INF/services}, because TestNG applies suite changes only
 * from listeners loaded before the suite file is read.
 */
public class BrowserParallelism implements IAlterSuiteListener {

  @Override
  public void alter(List<XmlSuite> suites) {
    if (DriverFactory.allowsParallelSessions()) {
      return;
    }
    for (XmlSuite suite : suites) {
      for (XmlTest test : suite.getTests()) {
        if (test.getName().equals("ui")) {
          test.setParallel(XmlSuite.ParallelMode.NONE);
          test.setThreadCount(1);
        }
      }
    }
  }
}
