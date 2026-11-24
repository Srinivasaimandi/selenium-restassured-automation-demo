# Selenium Automation Demo

UI and API tests for the [Users demo app](https://github.com/srinivasaimandi/users-app-demo), written with Selenium and Java. It is a port of the Playwright framework in `copilot-playwright-framework`: the same scenarios, test data and safety rules, rebuilt on a Java stack.

## Stack

| Concern | Tool |
| --- | --- |
| Browser automation | Selenium 4 (WebDriver, plus WebDriver BiDi for network mocks) |
| Test runner | TestNG 7, run by Maven Surefire |
| API testing | REST Assured, with JSON Schema validation |
| Accessibility | axe-core (`com.deque.html.axe-core:selenium`) |
| Assertions | AssertJ, plus waiting UI assertions in `Expect` |
| Reporting | Allure |
| Config | `.env` files read with dotenv-java |

## Prerequisites

- JDK 21 or newer
- Maven 3.9 or newer
- Node.js 22, which runs the app under test
- A browser:
  - **Chrome** is the default, locally and in CI. Selenium Manager downloads a matching chromedriver on first use, so the first run is slower. Don't keep an old `chromedriver` on your `PATH`: Selenium uses it instead of downloading one, and a version that doesn't match your Chrome fails with `DevToolsActivePort file doesn't exist`.
  - **Firefox** (`-Dbrowser=firefox`) is also fully supported.
  - **Safari** (`-Dbrowser=safari`) works with limits; see [Browser support](#browser-support). Enable automation once: run `safaridriver --enable`, then in Safari turn on **Develop › Allow Remote Automation**.

## Setup

```bash
git clone --recurse-submodules <this repo>
cd selenium-automation-demo
cp env/.env.example env/.env.local   # then fill in LOGIN_USERNAME and LOGIN_PASSWORD
```

The app lives in the `app/` git submodule. For local runs the suite starts it at `http://127.0.0.1:9899` (installing its npm dependencies the first time) and stops it afterwards. If an app is already running there, the suite reuses it. To test the latest app code, update the submodule first:

```bash
git submodule update --remote --init app
```

## Running tests

| Goal | Command |
| --- | --- |
| Everything, local app | `mvn test` |
| Only some groups | `mvn test -Dinclude.groups=smoke` |
| Leave groups out | `mvn test -Dexclude.groups=test-data` |
| Pick a browser | `mvn test -Dbrowser=firefox` (`chrome` is the default; `firefox` or `safari`) |
| Watch the browser | `mvn test -Dheadless=false` (Safari is never headless) |
| Another environment | `TEST_ENV=dev mvn test -Dexclude.groups=test-data` |
| Retry failures | `mvn test -Dretries=2` (CI retries twice by default) |

Groups: `setup`, `smoke`, `login`, `validation`, `auth`, `users`, `mocked`, `e2e`, `a11y`, `api`, `graphql`, `contract`, `test-data`, `destructive`, `known-defect`. The `setup` test always runs, because the UI tests depend on it.

## Environments

`TEST_ENV` selects `local` (default), `test`, `dev` or `staging`. Settings come from, in order of precedence: a `-D` system property, a process environment variable, then `env/.env.<TEST_ENV>`. Each environment has a committed `env/.env.*.example` template; the real files are git-ignored.

| Setting | Meaning |
| --- | --- |
| `BASE_URL` | App URL. Defaults to `http://127.0.0.1:9899` for `local`. |
| `LOGIN_USERNAME`, `LOGIN_PASSWORD` | Account for login, UI and API tests. Without them, tests that need a login are skipped. |
| `TEST_DATA_ALLOWED_HOSTS` | Comma-separated hosts where `TEST_ENV=test` may create and delete users. |

**Test data safety.** Tests that create, change or delete users (group `test-data`) only run against a loopback URL with `TEST_ENV=local`, or against an allowlisted host with `TEST_ENV=test`. Anywhere else they fail before sending a request. Leave them out on shared environments with `-Dexclude.groups=test-data`.

## Reports

```bash
mvn allure:serve    # build the report and open it in a browser
mvn allure:report   # build it into target/site/allure-maven-plugin
```

Failed UI tests attach a screenshot and the page source. API calls are attached as request and response pairs. The Allure plugin keeps trend history between runs in `.allure/`. Plain TestNG/Surefire results are in `target/surefire-reports`.

## How the suite runs

`src/test/resources/testng.xml` runs four sections in order. Tests inside a section run in parallel (four threads).

1. **setup**: logs in through the UI once and saves the session token.
2. **api**: REST, GraphQL and OpenAPI contract tests. Classes run in parallel; methods in a class run in order.
3. **ui**: UI tests, one fresh browser per test.
4. **api-destructive**: the data reset, which deletes users other tests rely on. It runs last and is skipped if any earlier test failed.

## Browser support

Network mocks (`UsersApiMock`) and the smoke test's console and network checks (`BrowserDiagnostics`) use WebDriver BiDi. Safari doesn't support BiDi yet.

| | Chrome (default) | Firefox | Safari |
| --- | --- | --- | --- |
| Most UI and all API tests | ✅ | ✅ | ✅ |
| Mocked tests (`mocked` group) and smoke diagnostics | ✅ | ✅ | skipped |
| Parallel UI tests | ✅ | ✅ | no (one session at a time) |
| Headless | ✅ | ✅ | no |

On Safari, those tests are reported as skipped with the reason, and the UI section switches to one test at a time automatically.

## Project layout

```
src/test/java/com/demo/
  config/     Environment settings; starts and stops the local app
  data/       Test data records and the seeded/mock users
  api/        REST Assured clients (REST, GraphQL), OpenAPI document reader, schema checks
  ui/         Driver factory, Expect (waiting assertions), browser diagnostics
    pages/    Page objects: LoginPage, UsersPage, Row
    mocks/    UsersApiMock (BiDi network mocks), FetchRecorder
  support/    Base test classes and TestNG listeners
  tests/      setup/, api/, api/destructive/, ui/, ui/e2e/
src/test/resources/
  testng.xml  Suite sections and listeners
  schemas/    JSON Schemas for API responses
```

## Writing tests

- **UI tests** extend `BaseUiTest`. Each test gets its own browser. Tests start signed out; call `openAuthenticatedUsersPage()` to start signed in with the shared session.
- **API tests** extend `BaseTest`. `usersApi()` and `graphqlApi()` log in once per test, so a test can log out without affecting others.
- **Users created for a test**: `try (ApiTestUser user = createApiTestUser()) { ... }` checks the target is safe, creates a unique user, and deletes it afterwards.
- **Assertions on the page** go through `expect()`, which waits until the page reaches the expected state (up to 5 seconds). Don't assert on a single `findElement` read.
- **Locators** are `By` fields on page objects. Prefer ids, then accessible names (`aria-label`, visible text).
- **Mocking the API**: `usersApiMock().fulfill(...)`, `.abort(...)`, `.hold(...)` and `.respondWithUsers(...)`. The newest mock wins, and unmatched requests reach the real app.
- **Known app defects**: annotate a test of the correct behaviour with `@KnownDefect("…")`. It is reported as passing while it fails, and fails once the defect is fixed, so the annotation gets removed.
- **Tags**: put every test in TestNG `groups`. Add `test-data` to anything that writes data.

## Formatting

Spotless is set up with Google Java Format:

```bash
mvn spotless:apply   # format
mvn spotless:check   # verify
```

## CI

`.github/workflows/selenium.yml` runs on every push and pull request on Ubuntu with Chrome. It checks formatting with Spotless, updates the app submodule to the latest `main`, runs the suite with two retries, builds the Allure report, and uploads the report, the Surefire results and the app log as the `test-results` artifact. Allure history is kept in the Actions cache for branch runs.

Add the repository secrets `TEST_LOGIN_USERNAME` and `TEST_LOGIN_PASSWORD` (the app's login) before the first run.
