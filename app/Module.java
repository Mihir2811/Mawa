import com.google.inject.AbstractModule;
import services.InMemoryResultCache;
import services.MastodonClient;
import services.MastodonClientImpl;
import services.MastodonService;
import services.MastodonServiceImpl;
import services.ResultCache;

/**
 * Wires interfaces to their real implementations for dependency injection.
 *
 * <p>This is also the single place tests need to override if they ever want
 * to swap in a fake implementation application-wide - in practice our unit
 * tests just build controllers/services directly with Mockito mocks instead
 * of going through Guice at all, but this module is what production
 * (`sbt run`) actually uses.</p>
 *
 * Registered automatically by Play via the {@code play.modules.enabled}
 * convention (any top-level {@code Module} class on the classpath is picked
 * up without extra configuration).
 *
 * @author MAWA Team
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        bind(MastodonClient.class).to(MastodonClientImpl.class);
        bind(MastodonService.class).to(MastodonServiceImpl.class);
        bind(ResultCache.class).to(InMemoryResultCache.class);
    }
}
