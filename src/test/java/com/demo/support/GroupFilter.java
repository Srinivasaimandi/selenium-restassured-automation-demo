package com.demo.support;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITestContext;

/**
 * Selects tests by TestNG group from system properties, like Playwright's {@code --grep} and {@code
 * --grep-invert}. Surefire ignores its own {@code groups} settings when a suite XML file is used,
 * so the filter lives here.
 *
 * <ul>
 *   <li>{@code -Dinclude.groups=smoke,api} runs only tests in at least one of these groups.
 *   <li>{@code -Dexclude.groups=test-data} drops tests in any of these groups.
 * </ul>
 *
 * Tests in the {@code setup} group always run, because other tests depend on them.
 */
public class GroupFilter implements IMethodInterceptor {

  private static final String SETUP_GROUP = "setup";

  @Override
  public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
    Set<String> include = groups("include.groups");
    Set<String> exclude = groups("exclude.groups");
    return methods.stream()
        .filter(method -> {
          List<String> groups = Arrays.asList(method.getMethod().getGroups());
          if (groups.contains(SETUP_GROUP)) {
            return true;
          }
          if (groups.stream().anyMatch(exclude::contains)) {
            return false;
          }
          return include.isEmpty() || groups.stream().anyMatch(include::contains);
        })
        .toList();
  }

  private static Set<String> groups(String property) {
    return Arrays.stream(System.getProperty(property, "").split(","))
        .map(String::trim)
        .filter(group -> !group.isEmpty())
        .collect(Collectors.toSet());
  }
}
