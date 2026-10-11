package dev.abbah.infra.api.rest.authorizationrequest;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.abbah.infra.spi.rest.salesforce.InjectSalesforceStub;
import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * Reads the spans in-process, so it has no packaged-mode {@code IT}.
 */
@QuarkusTest
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestTracingTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";
    private static final AttributeKey<String> URL_FULL = AttributeKey.stringKey("url.full");

    @InjectSalesforceStub
    WireMockServer salesforce;

    @Inject
    InMemorySpanExporter spans;

    @BeforeEach
    void resetSalesforce() {
        SalesforceStub.reset(salesforce);
    }

    @Test
    void retriedSalesforceCallsAppearInTheRequestTrace() {
        salesforce.stubFor(post(APEX_PATH).inScenario("recovery")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        salesforce.stubFor(post(APEX_PATH).inScenario("recovery")
                .whenScenarioStateIs("recovered")
                .willReturn(jsonResponse("""
                        {"caseId": "500x", "caseNumber": "00012345"}
                        """, 201)));
        // The caller's trace context makes the request's spans findable among those of earlier tests.
        String traceId = TraceId.fromLongs(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong());

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .header("traceparent", "00-%s-00f067aa0ba902b7-01".formatted(traceId))
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201);

        await().untilAsserted(() -> {
            List<SpanData> trace = spans.getFinishedSpanItems().stream()
                    .filter(span -> span.getTraceId().equals(traceId))
                    .toList();
            assertThat(trace.stream()
                    .filter(span -> span.getKind() == SpanKind.SERVER)
                    .filter(span -> span.getName().equals("POST /authorization-requests"))
                    .toList(), hasSize(1));
            assertThat(trace.stream()
                    .filter(span -> span.getKind() == SpanKind.CLIENT)
                    .filter(span -> String.valueOf(span.getAttributes().get(URL_FULL)).endsWith(APEX_PATH))
                    .toList(), hasSize(2));
        });
    }
}
