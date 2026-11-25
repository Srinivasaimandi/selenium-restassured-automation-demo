# Test Writing Standards

The rules every new or changed Selenium test in this repo must follow. They are derived from the existing suite
(`LoginTest`, `UsersScreenTest`, `MockedUsersTest`, `e2e/UsersJourneyTest` and `api/UsersApiTest` under
`src/test/java/com/demo/tests/`). When in doubt, copy the pattern used there.

## Where files go

Paths are relative to `src/test/java/com/demo/`.

| Kind of test                  | Location                                    | `testng.xml` section            |
| ----------------------------- | ------------------------------------------- | ------------------------------- |
| UI scenario on one screen     | `tests/ui/<Feature>Test.java`               | `ui` (parallel methods)         |
| Multi-screen user journey     | `tests/ui/e2e/<Journey>Test.java`           | `ui`                            |
| Accessibility scan            | `tests/ui/AccessibilityTest.java`           | `ui`                            |
| UI test with mocked network   | `tests/ui/MockedUsersTest.java`, `tests/ui/UsersApiFailureTest.java` | `ui` |
| REST API contract / CRUD      | `tests/api/<Resource>ApiTest.java`          | `api` (parallel classes)        |
| GraphQL API                   | `tests/api/<Resource>GraphqlTest.java`      | `api`                           |
| OpenAPI document checks       | `tests/api/OpenApiContractTest.java`        | `api`                           |
| Test that resets all app data | `tests/api/destructive/<Name>Test.java`     | `api-destructive` (runs last)   |
| Network mocks for UI tests    | `ui/mocks/`                                 | —                               |
| Page object                   | `ui/pages/<Page>Page.java`                  | —                               |
| API client                    | `api/` (`UsersApiClient`, `GraphqlClient`)  | —                               |
| Response JSON Schema          | `src/test/resources/schemas/<name>.json`, named in `api/Schemas.java` | — |
| Shared test data              | `data/TestData.java`, records in `data/`    | —                               |

Prefer adding a method to an existing test class for the same feature over creating a new class. A new class in an
existing package is picked up by `testng.xml` automatically; don't edit `testng.xml`.

## Base classes

- UI tests extend `BaseUiTest`, which gives each test its own browser. Use its accessors instead of constructing
  objects in tests: `driver()`, `loginPage()`, `usersPage()`, `expect()`, `usersApiMock()`, `axe()`,
  `openAuthenticatedUsersPage()` (starts signed in with the shared session).
- API tests extend `BaseTest`: `usersApi()`, `graphqlApi()`, `createApiTestUser()`.
- If a test needs a new reusable resource, add it to the base class or a helper next to it rather than setup code
  repeated across tests.

## Structure and naming

- Annotate each class with `@Feature("<Feature>")` and a one-line Javadoc comment above it.
- Every test method has `@Test(groups = ..., description = "...")`. The description is a lowercase behaviour
  statement: `"filters users by email without case sensitivity"`. The method name says the same in camelCase.
- Groups: `smoke` for fast critical-path checks, `login`, `users`, `validation` for input rules, `auth` for
  login-dependent checks, `e2e` on journeys, `a11y` on accessibility scans, `api` on API tests, `graphql` on GraphQL
  tests, `contract` on OpenAPI checks, `mocked` on UI tests that mock the network, and **`test-data` on any test that
  creates, edits or deletes data** (these are excluded from dev/staging runs).
- Link the test to its requirement in `requirement/` with Allure's `@Story("REQ-001: <short title>")` on the method.

## Locators and page objects

- All locators live in page objects as `public final By` fields; actions are small methods with a Javadoc line (and
  `@Step` where they show up in reports). Tests may build one-off row or cell lookups from page-object helpers such as
  `usersPage.userRow(name)` and `Row`.
- Locator priority: `By.id` → accessible name (`aria-label`, `role`, visible text via XPath `normalize-space()`) →
  CSS. Build XPath string literals with `XPath.literal(...)`, never by concatenating raw text.
- Scope locators to their container (dialog, row, table) instead of picking an index from a page-wide query.

## Assertions and waiting

- Assert on the page through `expect()` (`visible`, `hidden`, `count`, `text`, `texts`, `allTextsMatch`, `value`,
  `enabled`, `focused`, `urlMatches`, `poll`, …). It waits until the page reaches the expected state. Don't assert on a
  single `findElement` read.
- Never use `Thread.sleep`, fixed waits, or JavaScript clicks to get past an element that isn't clickable.
- Use AssertJ (`assertThat`) for values that are already settled, such as API responses or counts read after an
  `expect()` check.
- Every test asserts the outcome the requirement asks for, not just that steps ran without error.
- API responses are status-checked and schema-validated through `UsersApiClient`, `GraphqlClient` or
  `ApiResponses.parse(...)` / `parseError(...)`. When the client calls a new REST operation, add it to
  `UsersApiClient.OPERATIONS` so the contract test checks it, and add a schema file for its response.

## Accessibility and failure-state tests

- Scan with `axe().analyze(driver())`, which checks WCAG 2.1 A/AA rules, and assert there are no violations. Scope a
  scan with `.include(...)` instead of disabling rules.
- Mock the users API with `usersApiMock()` rather than BiDi calls in a test: `fulfill` for canned responses and error
  statuses, `respondWithUsers` for lists, `abort` for network failures, and `hold` to check the page while a response
  is slow (never a fixed delay). The newest mock wins, and requests the mock does not match reach the real app.
  Mocks need WebDriver BiDi; `BaseUiTest` skips them on Safari.
- To check what the page sent, use `FetchRecorder.install(driver())`; Selenium's BiDi does not expose request bodies
  yet.
- Mock rows that a test edits or deletes with `TestData.MockUsers`, whose IDs no real user has.
- Always mock the data reset in UI tests: a real reset deletes users that parallel tests depend on.

## Known app defects

- When the app does not yet behave as it should, write the test for the correct behaviour, annotate it with
  `@KnownDefect("<what the app does wrong>")` and add the `known-defect` group. Never weaken the assertion to make it
  pass.
- Check that the test fails for the reason in the annotation. When the app is fixed, the test fails as an unexpected
  pass: remove the annotation and the group.

## Data and environment safety

- Never hard-code credentials. Read them with `Environment.requireLoginCredentials("<purpose>")`, which skips the test
  with a clear message when they are unset.
- Reference data goes in `TestData`; generated data uses `TestData.NEW_USER.unique()`.
- Tests that create data use `try (ApiTestUser user = createApiTestUser()) { ... }`, which checks the target is safe
  and deletes the user afterwards. Other data-changing code calls `Environment.assertTestDataTargetIsSafe()` first,
  cleans up in a `finally` block, and is in the `test-data` group.
- Tests must be independent and parallel-safe: no reliance on order or on data another test creates.

## Quality gates

A change is done only when all of these pass:

```bash
mvn -B -ntp spotless:apply            # then spotless:check must be clean
mvn -B -ntp test-compile
mvn -B -ntp test -Dsuite=target/requirement-suite.xml   # suite listing only the changed classes, run 3 times
```

`target/requirement-suite.xml` is generated by the requirement-to-test orchestrator (see
`.claude/skills/requirement-to-test/SKILL.md`). Don't use `-Dtest=...`: it bypasses `testng.xml`, so the listeners
that start the app, filter groups and handle `@KnownDefect` don't run.
