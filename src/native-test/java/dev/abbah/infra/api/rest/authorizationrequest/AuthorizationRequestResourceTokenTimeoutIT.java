package dev.abbah.infra.api.rest.authorizationrequest;

import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusIntegrationTest
@TestProfile(AuthorizationRequestResourceTokenTimeoutTest.FreshStart.class)
// Repeated from the superclass: test resources are found through the index of this source set only.
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestResourceTokenTimeoutIT extends AuthorizationRequestResourceTokenTimeoutTest {
    // Execute the same tests but in packaged mode.
}
