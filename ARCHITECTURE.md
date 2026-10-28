# Architecture Diagram

```
                         Browser
                            |
                            |  HTTP GET /, /search
                            v
                 +----------------------+
                 |  MastodonController  |   (app/controllers)
                 |  - index()           |
                 |  - search()          |
                 +----------------------+
                            |
                            |  CompletionStage<SearchResult>
                            v
                 +----------------------+
                 |   MastodonService    |   (app/services, interface)
                 |  MastodonServiceImpl |
                 +----------------------+
                     |              |
                     |              |  cache-aside lookup
                     v              v
          +--------------------+  +------------------+
          |   MastodonClient   |  |   ResultCache    |
          |  (interface)       |  |  (interface)     |
          |  MastodonClientImpl|  | InMemoryResultCache|
          +--------------------+  +------------------+
                     |
                     |  HTTPS GET /api/v1/timelines/tag/{hashtag}
                     v
          +--------------------------+
          |  Mastodon REST API       |
          |  (configured instance)   |
          +--------------------------+
```

## Analysis components (run inside MastodonServiceImpl, after the client returns)

```
            List<Post>  (from MastodonClient)
                  |
                  v
        +----------------------+
        |  SentimentAnalyzer    |   (app/services)
        |  - analyzeAll(posts)  |  -> tags each Post with a Sentiment
        |  - summarize(posts)   |  -> produces SearchStatistics
        +----------------------+
                  |
                  v
             SearchResult   (posts + stats + pagination cursor)
                  |
                  v
           results.scala.html   (rendered back to the browser)
```

## Notes on this diagram

- `MastodonController` never talks to `MastodonClient` or Mastodon
  directly - it only knows about `MastodonService`. This is what section
  5.3 means by "do not put business logic into the controller".
- `MastodonClient` is the **only** class that knows the shape of Mastodon's
  JSON responses. Everything above it in the diagram works purely with our
  own `Post` / `SearchResult` / `SearchStatistics` models.
- `ResultCache` sits next to `MastodonClient` rather than inside it, so
  caching is a concern of the service layer (deciding *when* to avoid a
  Mastodon call), not the client (which only knows *how* to make one).
- See `DESIGN.md` for how this same diagram is meant to extend into
  Delivery 2 (streaming API + actors + WebSockets).
