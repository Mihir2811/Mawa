package services;

import models.SearchResult;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Business-logic layer sitting between the controller and the low-level
 * {@link MastodonClient}. This is where fetching, caching, and sentiment
 * analysis all get wired together, so the controller itself stays thin
 * (assignment 5.3: "Do not put business logic into the controller").
 *
 * @author MAWA Team
 */
public interface MastodonService {

    /**
     * Searches for one page of posts for the given hashtag, already
     * sentiment-tagged and with statistics computed.
     *
     * @param hashtag hashtag without the leading '#'
     * @param maxId   optional pagination cursor (see {@link MastodonClient})
     * @return a future {@link SearchResult} ready to hand to the view
     */
    CompletionStage<SearchResult> search(String hashtag, Optional<String> maxId);
}
