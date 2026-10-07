package g_mungus.munguscript.conformance;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A test run once against every engine implementation on the classpath, each time with a fresh
 * {@link Harness} as its argument.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest(name = "{displayName} on {0}")
@ArgumentsSource(EngineProviders.class)
@interface EngineTest {
}
