package dev.abbah.infra.api.rest.authorizationrequest;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.abbah.infra.spi.rest.salesforce.InjectSalesforceStub;
import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Reads the meters in-process, so it has no packaged-mode {@code IT}.
 */
@QuarkusTest
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestMetricsTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";

    @InjectSalesforceStub
    WireMockServer salesforce;

    @Inject
    SimpleMeterRegistry registry;

    @BeforeEach
    void resetSalesforce() {
        SalesforceStub.reset(salesforce);
    }

    @Test
    void failedRequestIsCountedByRouteAndStatus() {
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(500)));
        long before = badGatewayCount();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(502);

        // The meter is recorded when the response ends, which can follow the client receiving it.
        await().untilAsserted(() -> assertThat(badGatewayCount(), is(before + 1)));
    }

    @Test
    void salesforceCallsAndRetriesAreMetered() {
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(500)));

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(502);

        await().untilAsserted(() -> {
            assertThat(registry.find("http.client.requests").timer(), notNullValue());
            assertThat(registry.find("ft.retry.calls.total").counter(), notNullValue());
            assertThat(registry.find("jvm.memory.used").gauge(), notNullValue());
        });
    }

    private long badGatewayCount() {
        Timer timer = registry.find("http.server.requests")
                .tag("uri", "/authorization-requests")
                .tag("status", "502")
                .timer();
        return timer == null ? 0 : timer.count();
    }
}
