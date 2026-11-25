# Selenium Automation Demo

UI and API tests for the [Users demo app](https://github.com/srinivasaimandi/users-app-demo), written with Selenium and Java. It is a port of the Playwright framework in `copilot-playwright-framework`: the same scenarios, test data and safety rules, rebuilt on a Java stack.

For the full developer guide (architecture, base classes, API validation, the requirement-to-test orchestrator, file reference and more), open [`docs/index.html`](docs/index.html) in a browser. Test conventions are in [`docs/test-writing-standards.md`](docs/test-writing-standards.md).

## What it covers

- **UI**: login, users screen, smoke checks with console and network diagnostics, end-to-end journeys, axe-core
  accessibility scans, and network-mocked failure states, in Chrome (Firefox fully supported, Safari with limits).
- **API**: REST, the GraphQL API, and the published OpenAPI contract, with JSON Schema validation of responses.
- **Tooling**: per-environment config, safe disposable test data, Allure reports with trend history, GitHub
  Actions CI, and a Claude Code orchestrator that writes tests from requirements using a Selenium MCP browser.

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

## Environments

`TEST_ENV` selects `local` (default), `test`, `dev` or `staging`. Settings come from, in order of precedence: a `-D` system property, a process environment variable, then `env/.env.<TEST_ENV>`. Each environment has a committed `env/.env.*.example` template; the real files are git-ignored.

| Setting | Meaning |
| --- | --- |
| `BASE_URL` | App URL. Defaults to `http://127.0.0.1:9899` for `local`. |
| `LOGIN_USERNAME`, `LOGIN_PASSWORD` | Account for login, UI and API tests. Without them, tests that need a login are skipped. |
| `TEST_DATA_ALLOWED_HOSTS` | Comma-separated hosts where `TEST_ENV=test` may create and delete users. |

**Test data safety.** Tests that create, change or delete users (group `test-data`) only run against a loopback URL with `TEST_ENV=local`, or against an allowlisted host with `TEST_ENV=test`. Anywhere else they fail before sending a request. Leave them out on shared environments with `-Dexclude.groups=test-data`.

## How the suite runs

`src/test/resources/testng.xml` runs four sections in order. Tests inside a section run in parallel (four threads).

1. **setup**: logs in through the UI once and saves the session token.
2. **api**: REST, GraphQL and OpenAPI contract tests. Classes run in parallel; methods in a class run in order.
3. **ui**: UI tests, one fresh browser per test.
4. **api-destructive**: the data reset, which deletes users other tests rely on. It runs last and is skipped if any earlier test failed.

## Tags

Every test is in one or more TestNG groups: `setup`, `smoke`, `login`, `validation`, `auth`, `users`, `mocked`, `e2e`, `a11y`, `api`, `graphql`, `contract`, `test-data`, `destructive`, `known-defect`. The `setup` test always runs, because the UI tests depend on it.

Filter with `-Dinclude.groups=<group>` or exclude with `-Dexclude.groups=<group>`.

## Browser support

Network mocks (`UsersApiMock`) and the smoke test's console and network checks (`BrowserDiagnostics`) use WebDriver BiDi. Safari doesn't support BiDi yet.

| | Chrome (default) | Firefox | Safari |
| --- | --- | --- | --- |
| Most UI and all API tests | ✅ | ✅ | ✅ |
| Mocked tests (`mocked` group) and smoke diagnostics | ✅ | ✅ | skipped |
| Parallel UI tests | ✅ | ✅ | no (one session at a time) |
| Headless | ✅ | ✅ | no |

On Safari, those tests are reported as skipped with the reason, and the UI section switches to one test at a time automatically.

## Reports

```bash
mvn allure:serve    # build the report and open it in a browser
mvn allure:report   # build it into target/site/allure-maven-plugin
```

Failed UI tests attach a screenshot and the page source. API calls are attached as request and response pairs. The Allure plugin keeps trend history between runs in `.allure/`. Plain TestNG/Surefire results are in `target/surefire-reports`.

## Project structure

```text
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
docs/         Developer guide (index.html) and test-writing standards
requirement/  Requirements read by the requirement-to-test orchestrator
.claude/      Orchestrator skill and writer/healer agents
.mcp.json     Selenium MCP server
```

## Writing tests

The full conventions are in [`docs/test-writing-standards.md`](docs/test-writing-standards.md). In short:

- **UI tests** extend `BaseUiTest`. Each test gets its own browser. Tests start signed out; call `openAuthenticatedUsersPage()` to start signed in with the shared session.
- **API tests** extend `BaseTest`. `usersApi()` and `graphqlApi()` log in once per test, so a test can log out without affecting others.
- **Users created for a test**: `try (ApiTestUser user = createApiTestUser()) { ... }` checks the target is safe, creates a unique user, and deletes it afterwards.
- **Assertions on the page** go through `expect()`, which waits until the page reaches the expected state (up to 5 seconds). Don't assert on a single `findElement` read.
- **Locators** are `By` fields on page objects. Prefer ids, then accessible names (`aria-label`, visible text).
- **Mocking the API**: `usersApiMock().fulfill(...)`, `.abort(...)`, `.hold(...)` and `.respondWithUsers(...)`. The newest mock wins, and unmatched requests reach the real app.
- **Tags**: put every test in TestNG `groups`. Add `test-data` to anything that writes data.

### Known app defects

Annotate a test of the correct behaviour with `@KnownDefect("…")`. It is reported as passing while it fails, and fails once the defect is fixed, so the annotation gets removed.

## Formatting and linting

Spotless is set up with Google Java Format:

```bash
mvn spotless:apply   # format
mvn spotless:check   # verify
```

## Continuous integration

`.github/workflows/selenium.yml` runs on every push and pull request on Ubuntu with Chrome. It checks formatting with Spotless, updates the app submodule to the latest `main`, runs the suite with two retries, builds the Allure report, and uploads the report, the Surefire results and the app log as the `test-results` artifact. Allure history is kept in the Actions cache for branch runs.

Add the repository secrets `TEST_LOGIN_USERNAME` and `TEST_LOGIN_PASSWORD` (the app's login) before the first run.

## Selenium MCP

The root `.mcp.json` configures the community [Selenium MCP server](https://github.com/angiejones/mcp-selenium) (`@angiejones/mcp-selenium`, pinned to 0.2.3 and run with `npx`), which lets an AI assistant drive a live Chrome, Firefox, Edge or Safari session through Selenium WebDriver. Claude Code reads the file when opened at the repository root and asks you to approve the `selenium` server; the orchestrator's agents use it to explore the running app. Copilot in VS Code can use the same file: trust the workspace/server when prompted, then start the `selenium` server from the MCP servers UI. If VS Code does not load workspace-root MCP configuration, enable the `chat.mcp.workspaceRootConfig.enabled` setting.

The MCP server is an agent-facing browser tool; automated test runs use TestNG and `DriverFactory` directly, and CI does not use it. Its browser is a separate session: it doesn't get the shared login token, the BiDi network mocks or the suite's window size. Start the app first with `npm --prefix app start` (the suite only runs it during `mvn test`), point the MCP browser only at the local app (the test-data safety checks don't cover clicks made through MCP), and never put `LOGIN_USERNAME` or `LOGIN_PASSWORD` in `.mcp.json`.

## Requirement-to-test orchestrator (Claude Code)

`/requirement-to-test <requirement-id-or-path>` turns a requirement from the [`requirement/`](requirement/) folder
into Selenium tests. Nothing is committed or pushed until you approve. The
[developer guide](./docs/index.html#orchestrator) covers the flow in detail.

```bash
/requirement-to-test REQ-001
```

### Prerequisites

- Claude Code opened at the repository root, with the `selenium` MCP server from `.mcp.json` enabled.
- JDK, Maven, Node.js and Chrome installed as in [Prerequisites](#prerequisites), and the app submodule set up as in
  [Setup](#setup).
- `LOGIN_USERNAME` and `LOGIN_PASSWORD` in `env/.env.local`. Without them, authenticated tests skip and the
  orchestrator reports them as not verified.

### Writing a requirement

Copy `requirement/_template.md` to `requirement/REQ-<nnn>-<slug>.md`, describe the feature, list numbered
acceptance criteria (`AC1`, `AC2`, …) as observable outcomes, and set `status: ready`. Each criterion becomes at
least one test. See [`requirement/README.md`](requirement/README.md) for the format and
[`REQ-001-user-search.md`](requirement/REQ-001-user-search.md) for an example. Generated tests carry the Allure
annotation `@Story("REQ-001: <short title>")`, so you can find a requirement's tests by its ID.

### What a run does

1. **Preconditions**: finds the requirement file, warns about uncommitted test changes, and creates the branch
   `tests/<id>` (for example `tests/req-001`).
2. **Read**: extracts acceptance criteria, out-of-scope notes and test-data hints. A `draft` requirement or unclear
   criteria make it stop and ask.
3. **Plan**: maps each criterion to a test class, method and groups, skipping criteria that existing tests already
   cover.
4. **Write**: the `selenium-test-writer` agent checks locators in the live app through Selenium MCP, then adds test
   classes, page objects and test data.
5. **Verify**: runs `spotless:check`, `test-compile`, and a generated suite (`target/requirement-suite.xml`) holding
   only the changed classes, with the same listeners as `testng.xml` and no retries.
6. **Heal**: on failure, the `selenium-test-healer` agent works out whether the test, the app or the environment
   is at fault, and fixes only test defects. At most three attempts; it stops early on an app or environment
   problem.
7. **Stability**: runs the passing suite two more times to catch flaky tests.
8. **Approve**: shows coverage per criterion, the diff, run results and suspected app defects, then asks you to
   **approve and commit** (no push), **request changes** (back to step 4 with your notes), or **reject and
   discard** (removes the run's changes and branch).

The healer never makes a test pass by loosening or deleting an assertion tied to a criterion, disabling or skipping
the test, adding `@KnownDefect`, adding `Thread.sleep` or JavaScript clicks, raising timeouts, or editing `app/`,
`pom.xml`, `testng.xml` or the framework support code. The orchestrator checks the diff after each heal and reverts
any weakened assertion.

| File                                          | Role                                                                      |
| --------------------------------------------- | ------------------------------------------------------------------------- |
| `requirement/*.md`                            | Requirements with numbered acceptance criteria (the orchestrator's input) |
| `.claude/skills/requirement-to-test/SKILL.md` | Orchestrator: phases, heal limit, approval step                           |
| `.claude/agents/selenium-test-writer.md`      | Explores the app with Selenium MCP and writes the tests                   |
| `.claude/agents/selenium-test-healer.md`      | Diagnoses failures and fixes test defects only (never weakens assertions) |
| `docs/test-writing-standards.md`              | The writing standard both agents follow                                   |
