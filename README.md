# MAWA - Mastodon Analysis Web App

Project Delivery 1 for SOEN 6481 (Play Framework / Java), plus the first
piece of Delivery 2 (see "Delivery 2 progress" near the bottom). MAWA lets you
search Mastodon by hashtag, see the retrieved posts in a readable format,
and get sentiment analysis + aggregate statistics on what comes back.

> GenAI disclosure: parts of this project (scaffolding, Javadoc comments,
> and a first pass of several classes/tests) were generated with the help
> of Claude (Anthropic) and then reviewed/adjusted by the team. See the
> top of `build.sbt` and the per-file comments for where this applies.

## 1. Installing & running the app

Prerequisites: JDK 11+ and `sbt` (1.9+) installed.

```bash
git clone <your-repo-url>
cd mawa

# configure the Mastodon instance + token (see section 2 below), e.g.:
export MASTODON_INSTANCE=mastodon.social
export MASTODON_ACCESS_TOKEN=your-teams-token-here

sbt run
```

Then open <http://localhost:9000> in a browser.

sbt will download dependencies the first time, which can take a minute or
two. All the standard targets work:

```bash
sbt run       # run the dev server with hot reload
sbt test      # run the unit test suite (JUnit 5 + Mockito)
sbt jacoco    # generate the branch coverage report (target/scala-2.13/jacoco/report/html/index.html)
sbt javadoc   # generate Javadoc (target/scala-2.13/api/)
sbt clean     # wipe build artifacts

Terminal command to run code with just sbt.exe

PS C:\Users\p_mihirv\Downloads\mawa\mawa> & "C:\Users\p_mihirv\Downloads\sbt-2.0.12\sbt\bin\sbt.bat" --client=false

```

## 2. Configuring the Mastodon instance

Set the `MASTODON_INSTANCE` environment variable to the hostname of the
instance to query (no `https://`, no trailing slash), e.g.:

```bash
export MASTODON_INSTANCE=mastodon.social
```

If you don't set it, it defaults to `mastodon.social` (see
`conf/application.conf`).

## 3. Configuring the access token

Each team must register its own dedicated Mastodon application/account
(assignment section 3) and obtain an access token with at least the
`read:statuses` permission. Set it via:

```bash
export MASTODON_ACCESS_TOKEN=your-token-here
```

The token is **never** hardcoded anywhere in the source - it's only ever
read from this environment variable at startup (`conf/application.conf` ->
`MastodonClientImpl`). `.env.example` shows the two variables you need; copy
it to `.env` (which is gitignored) if your workflow uses a `.env` loader, or
just `export` them directly.

## 4. Using the application

1. Open the home page and type a hashtag **without** the `#` character
   (e.g. `java`, not `#java`).
2. Click **Search**. You'll see:
   - Aggregate stats at the top: total / positive / negative / neutral posts.
   - Each retrieved post: author, date, language, readable content (HTML
     stripped), visibility, a link to the original post, and a sentiment
     emoticon (`:-)` / `:-(` / `:-|`).
3. If there are more results available, click **Load more** at the bottom
   to fetch the next page (this uses Mastodon's `max_id` pagination, it
   does not re-fetch posts you've already seen).
4. If something goes wrong (bad hashtag, Mastodon down, etc.) you'll get a
   plain-English error message instead of a stack trace.

## 5. Running the tests

```bash
sbt test
```

Tests live under `test/` and mirror the `app/` package structure:

- `test/services/SentimentAnalyzerTest.java` - the sentiment analysis /
  statistics logic (equivalence classes + the 70% threshold boundary cases).
- `test/services/MastodonClientImplTest.java` - the Mastodon REST client,
  with `WSClient` mocked (covers every HTTP status / malformed-data branch).
- `test/services/MastodonServiceImplTest.java` - caching behavior,
  pagination cursor logic, error propagation.
- `test/controllers/MastodonControllerTest.java` - every controller branch,
  including each `MastodonApiException.Reason` mapping to the right HTTP
  status.
- `test/actors/SentimentAnalysisActorTest.java` - the Delivery 2
  `SentimentAnalysisActor` (see below), using the Java Pekko Testkit
  (`ActorTestKit` + `TestProbe`).

No test ever calls the live Mastodon API - `MastodonClient` is always
mocked except in `MastodonClientImplTest`, where `WSClient` itself is mocked
instead.

## 8. Delivery 2 progress

So far, Delivery 2 has just the individual-part requirement: a Pekko Typed
actor (`app/actors/SentimentAnalysisActor.java`) that does the same
sentiment-analysis work as `services.SentimentAnalyzer`, exposed through its
own message protocol (`AnalyzePosts` in, `SentimentAnalysisResponse` out),
with its own test suite built on the Java Pekko Testkit. It deliberately
wraps the existing `SentimentAnalyzer` rather than reimplementing the logic,
and it is **not yet wired into the WebSocket/supervisor layer** - that's the
group part of Delivery 2 (reactive server push, per-user actors, a
supervisor) and is still to be built. See the Javadoc at the top of
`SentimentAnalysisActor` for how it's meant to plug into that later.

A heads-up on `build.sbt`: Pekko is version-sensitive (every `pekko-*`
module on the classpath must match). I pinned `pekkoVersion := "1.0.2"`
as a reasonable guess for what Play 3.0.1 pulls in transitively, but
please run `sbt evicted` after your first `sbt compile` and bump that
number if sbt reports a conflicting version - I couldn't verify the exact
resolved version without a live build environment.

## 6. Main architectural decisions

See `ARCHITECTURE.md` for the component diagram and `DESIGN.md` for the
fuller design write-up (domain model, responsibility breakdown, how
Mastodon is isolated, where/why Streams are used, and how this evolves
into Delivery 2). The short version:

- **Layered design**: Controller -> Service -> Client, each hidden behind
  an interface, so business logic never sits in the controller and the
  Mastodon REST API never leaks past `MastodonClient`.
- **Our own `Post` domain model**, independent of the Mastodon JSON shape,
  so Delivery 2 (streaming API) can produce the same `Post` objects without
  touching the rest of the app.
- **Fully async controller actions** (`CompletionStage<Result>`), no
  blocking calls anywhere outside of tests.
- **Caching** via a small `ResultCache` interface (in-memory TTL cache for
  now) so repeat searches for the same hashtag/page don't re-hit Mastodon.

## 7. Assumptions & limitations

- We assume "statistical analysis" and "sentiment analysis" (the two
  individual tasks implemented here) are evaluated together since the
  stats are directly derived from the sentiment results - both were built
  by the same team member.
- The other individual tasks described in the assignment (post readability,
  author analysis, word stats) are **not** implemented in this submission;
  they're each a different team member's 50% and are out of scope for the
  code in this repository, though the architecture (particularly the
  `ResultCache`-backed post list per hashtag) was deliberately designed so
  a teammate can add a results-page link to a new stats view without
  needing to re-fetch from Mastodon.
- The happy/sad word and emoji lists in `SentimentAnalyzer` are a small,
  hand-curated set built for this project, not a research-grade lexicon -
  see the Javadoc on that class for the exact "> 70%" interpretation we
  went with, since the assignment text is a little ambiguous about the
  denominator.
- The in-memory cache (`InMemoryResultCache`) is per-instance and resets on
  restart; fine for this assignment, but would need to become a shared
  cache (Redis, etc.) behind the same `ResultCache` interface for a
  multi-instance deployment.
- No authentication/user accounts - this is a single shared search tool,
  per the assignment scenario.
- **Please actually run `sbt compile` / `sbt test` yourself before relying on
  this.** This project was written by hand without a live sbt/network
  environment to compile against, so while every file was written and
  reviewed carefully, there's a real chance of small issues on a first
  build - a wrong import, an sbt-jacoco setting key that's named slightly
  differently in whatever plugin version resolves, a Twirl template
  hiccup. Budget time to fix a few rough edges rather than assuming it
  builds green on the first try. The design/logic is solid; the "does it
  compile on sbt version X" part is the part we couldn't verify here.
