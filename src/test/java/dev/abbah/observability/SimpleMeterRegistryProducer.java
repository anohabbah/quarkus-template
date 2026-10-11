package dev.abbah.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Keeps the meters' values where tests can read them. The Micrometer-OpenTelemetry bridge registry
 * forwards every value to OpenTelemetry and keeps none, so its counts read 0. Quarkus adds this registry
 * to the composite next to it, and it receives the same meters.
 */
@ApplicationScoped
public class SimpleMeterRegistryProducer {

    @Produces
    @Singleton
    SimpleMeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
