package models;

/**
 * The three possible sentiment buckets a post can fall into.
 *
 * <p>We deliberately keep this to three values because that's what the
 * assignment spec asks for (happy / sad / neutral, see section 4.3,
 * "Sentiment analysis"). Each value carries the little emoticon the UI
 * is supposed to show next to it.</p>
 *
 * @author MAWA Team
 */
public enum Sentiment {

    HAPPY(":-)"),
    SAD(":-("),
    NEUTRAL(":-|");

    private final String symbol;

    Sentiment(String symbol) {
        this.symbol = symbol;
    }

    /**
     * @return the emoticon string to display for this sentiment, e.g. ":-)"
     */
    public String getSymbol() {
        return symbol;
    }
}
