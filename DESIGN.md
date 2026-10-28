# Design Document

## Domain model

- **`Post`** - our own representation of a single Mastodon status: id,
  author display name/handle, creation time, language, plain-text content
  (HTML already stripped), URL, visibility, and an attached `Sentiment`
  (set after the fact, not part of the raw Mastodon data).
- **`Sentiment`** - a 3-value enum (`HAPPY` / `SAD` / `NEUTRAL`), each
  carrying its display emoticon.
- **`SearchStatistics`** - aggregate counts (total/positive/negative/neutral)
  for one page of results.
- **`SearchResult`** - the hashtag searched, the list of (already tagged)
  posts, the `SearchStatistics`, and an `Optional<String> nextMaxId` for
  pagination.

None of these models know anything about HTTP, JSON, or the Mastodon API -
they're plain Java objects the rest of the app is built around.

## Responsibility of each major component

| Component | Responsibility |
|---|---|
| `MastodonController` | Reads the HTTP request, calls `MastodonService`, picks a view/status code. No business logic. |
| `MastodonService` / `MastodonServiceImpl` | Orchestrates a search: normalizes the hashtag, checks the cache, calls `MastodonClient` on a miss, runs `SentimentAnalyzer`, computes the next pagination cursor. |
| `MastodonClient` / `MastodonClientImpl` | The *only* component that talks to the Mastodon REST API and parses its JSON. Converts raw responses into `Post` objects or a `MastodonApiException`. |
| `ResultCache` / `InMemoryResultCache` | Cache-aside storage for `SearchResult`s, keyed by `hashtag:maxId`, so identical searches don't re-hit Mastodon within the TTL. |
| `SentimentAnalyzer` | Computes per-post sentiment and rolls it up into statistics. Pure, stateless, Streams-based logic - no I/O. |
| `MastodonApiException` | A single exception type (with a `Reason` enum) that every failure mode funnels through, so the controller has one place to turn failures into user-facing messages/status codes. |

## How Mastodon is isolated from the rest of the application

Only `MastodonClientImpl` imports anything related to Mastodon's actual
JSON shape (field names like `"acct"`, `"content"`, `"created_at"`, etc.).
Everything above it - the service, the controller, the views, the sentiment
analysis - only ever sees `Post`. This matters for two reasons:

1. **Testability**: every other layer can be unit tested against a mocked
   `MastodonClient`/`MastodonService` interface, never the real API.
2. **Delivery 2**: the plan is to add a second way of producing `Post`
   objects - from the Mastodon *streaming* API, via Pekko actors - without
   changing `Post`, `SentimentAnalyzer`, or the views at all. Only a new
   "streaming producer of `Post`s" component gets added alongside
   `MastodonClient`; the rest of the pipeline is designed to not care where
   the `Post`s came from.

## Where Java Streams are used, and why

- `SentimentAnalyzer.analyze(String)` - tokenizes content and counts
  happy/sad word matches with `Arrays.stream(...).filter(...).count()`.
- `SentimentAnalyzer.analyzeAll(List<Post>)` - `posts.forEach(...)` to tag
  every post with its computed sentiment in one pass.
- `SentimentAnalyzer.summarize(List<Post>)` - `Collectors.groupingBy(...,
  Collectors.counting())` to turn a list of sentiment-tagged posts directly
  into per-bucket counts, which is exactly the
  filter/map/groupingBy/counting/collect pattern the assignment calls out
  in section 4.3 ("Statistical Analysis").

These two methods are the required individual-task Streams usage
(sentiment analysis + statistical analysis). We didn't force Streams into
places where a plain loop was clearly clearer (e.g. the JSON-to-`Post`
mapping in `MastodonClientImpl`, which is inherently about handling
per-element parse failures with early exits/exceptions, not a
filter/map/reduce pipeline).

## How this evolves toward Delivery 2

Delivery 2 adds the Mastodon Streaming API -> Pekko -> actor-based
processing -> Play WebSocket -> Browser pipeline described in the
assignment. The Delivery 1 design supports this because:

- `Post` is already source-agnostic - a streaming-API listener can build
  the exact same `Post` objects `MastodonClientImpl` builds today.
- `SentimentAnalyzer` is pure and stateless, so it can be called from
  inside an actor just as easily as from `MastodonServiceImpl` today -
  no rework needed.
- `MastodonController` currently returns a fully-rendered HTML page per
  request; in Delivery 2 we'd add a WebSocket endpoint alongside the
  existing REST-based one (not replacing it, since Delivery 1's
  conventional request/response flow still needs to work), which pushes
  newly-tagged `Post`s to the browser as an actor receives them from the
  stream.
- The current `ResultCache`/`MastodonService` split means the caching
  decision ("have we already got this hashtag's data?") stays separate
  from how the data arrives, so it doesn't need to change shape when a
  second data-arrival path (streaming) is introduced.
