package g_mungus.munguscript.conformance;

import g_mungus.munguscript.engine.spi.EngineProvider;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Every {@link EngineProvider} registered on the test classpath, as a fresh {@link Harness} each.
 * An experimental engine is put under test by listing it in
 * {@code src/test/resources/META-INF/services/g_mungus.munguscript.engine.spi.EngineProvider}.
 */
final class EngineProviders implements ArgumentsProvider {

    static List<EngineProvider> all() {
        List<EngineProvider> providers = ServiceLoader.load(EngineProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (providers.isEmpty()) {
            throw new IllegalStateException("No EngineProvider is registered on the test classpath");
        }
        return providers;
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return all().stream().map(provider -> Arguments.of(new Harness(provider)));
    }
}
