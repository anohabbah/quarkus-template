package dev.abbah.observability;

import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
// Repeated from the superclass: test resources are found through the index of this source set only.
@WithTestResource(SalesforceStub.class)
class HealthIT extends HealthTest {
    // Execute the same tests but in packaged mode.
}
