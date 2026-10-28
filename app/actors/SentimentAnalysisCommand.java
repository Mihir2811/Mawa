package actors;

/**
 * Marker interface for every message {@link SentimentAnalysisActor} can
 * receive. Pekko Typed requires one common message type per actor so the
 * compiler can check that only messages it actually understands get sent to
 * it - this is that type.
 *
 * <p>Right now there's only one concrete command ({@link AnalyzePosts}), but
 * keeping the sealed-by-convention interface in place makes it trivial to
 * add more later (e.g. a "reset my cached word lists" admin command)
 * without changing the actor's declared message type.</p>
 *
 * @author MAWA Team (Delivery 2 - individual part)
 */
public interface SentimentAnalysisCommand {
}
