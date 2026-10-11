package dev.abbah.infra.api.rest.authorizationrequest;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.abbah.infra.spi.rest.salesforce.InjectSalesforceStub;
import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import dev.abbah.observability.LogCapture;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.EventData;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Reads the log records in-process, so it has no packaged-mode {@code IT}.
 */
@QuarkusTest
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestFailureLogTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";

    @RegisterExtension
    LogCapture logs = new LogCapture();

    @InjectSalesforceStub
    WireMockServer salesforce;

    @Inject
    InMemorySpanExporter spans;

    @BeforeEach
    void resetSalesforce() {
        SalesforceStub.reset(salesforce);
    }

    @Test
    void salesforceServerErrorIsLoggedOnce() {
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

        List<ExtLogRecord> errors = errorRecords();
        assertThat(errors, hasSize(1));
        assertThat(causeChain(errors.getFirst()), containsString("500"));
    }

    @Test
    void salesforceAnsweringTooLateIsLoggedWithTheTimeout() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"caseId": "500x", "caseNumber": "00012345"}
                """, 201).withFixedDelay(2 * SalesforceStub.READ_TIMEOUT_MILLIS)));

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

        List<ExtLogRecord> errors = errorRecords();
        assertThat(errors, hasSize(1));
        assertThat(causeChain(errors.getFirst()), containsStringIgnoringCase("timeout"));
    }

    @Test
    void salesforceResponseWithoutCaseNumberIsLogged() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("{}", 201)));

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

        List<ExtLogRecord> errors = errorRecords();
        assertThat(errors, hasSize(1));
        assertThat(causeChain(errors.getFirst()), containsString("no case number"));
    }

    @Test
    void unknownRequesterIsNotLoggedAsAnError() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"errorCode": "REQUESTER_NOT_FOUND"}
                """, 422)));

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "nobody@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(422);

        assertThat(errorRecords(), empty());
    }

    @Test
    void reusedIdempotencyKeyIsNotLoggedAsAnError() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"errorCode": "REQUEST_ID_REUSED"}
                """, 409)));

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(409);

        assertThat(errorRecords(), empty());
    }

    @Test
    void filedCaseIsNotLoggedAsAnError() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"caseId": "500x", "caseNumber": "00012345"}
                """, 201)));

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201);

        assertThat(errorRecords(), empty());
    }

    @Test
    void failureLogCarriesTheIdempotencyKey() {
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(500)));

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70")
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(502);

        List<ExtLogRecord> errors = errorRecords();
        assertThat(errors, hasSize(1));
        assertThat(errors.getFirst().getMdc("idempotencyKey"), is("7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70"));
    }

    @Test
    void eachFailureLogCarriesItsOwnIdempotencyKey() {
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(500)));
        List<String> keys = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        for (String key : keys) {
            given()
              .contentType(ContentType.JSON)
              .header("Idempotency-Key", key)
              .body("""
                    {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                     "authorizations": ["READ_PAYROLL"]}
                    """)
              .when().post("/authorization-requests")
              .then()
                 .statusCode(502);
        }

        assertThat(errorRecords().stream().map(record -> record.getMdc("idempotencyKey")).toList(),
                contains(keys.toArray()));
    }

    @Test
    void failureLogCarriesTheTraceOfTheRequest() {
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(500)));
        // The caller's trace context makes the request's server span findable among those of earlier tests.
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
             .statusCode(502);

        List<ExtLogRecord> errors = errorRecords();
        assertThat(errors, hasSize(1));
        await().untilAsserted(() -> assertThat(spans.getFinishedSpanItems().stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .filter(span -> span.getTraceId().equals(traceId))
                .toList(), hasSize(1)));
        assertThat(errors.getFirst().getMdc("traceId"), is(traceId));
    }

    @Test
    void failedOnboardingRequestLeaksNoPersonalData() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"errorCode": "INTERNAL", "message": "Could not file a case for jane.doe@corp.com"}
                """, 500)));
        String traceId = TraceId.fromLongs(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong());

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .header("traceparent", "00-%s-00f067aa0ba902b7-01".formatted(traceId))
          .body("""
                {"type": "ONBOARD", "requestedBy": "alice.admin@corp.com", "firstName": "Jane", "lastName": "Doe",
                 "email": "jane.doe@corp.com", "department": "Finance", "startDate": "2026-10-01",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(502);

        List<String> logged = logs.records().stream()
                .flatMap(record -> loggedText(record).stream())
                .toList();
        List<String> spanText = spanText(traceId);
        for (String personalData : List.of("alice.admin@corp.com", "jane.doe@corp.com", "Jane", "Doe", "Finance")) {
            assertThat(logged, everyItem(not(containsString(personalData))));
            assertThat(spanText, everyItem(not(containsString(personalData))));
        }
    }

    @Test
    void tokenEndpointErrorBodyIsNotLogged() {
        // The 401 makes the OIDC filter fetch a new token, whatever token earlier tests left cached.
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(401)));
        salesforce.stubFor(post("/services/oauth2/token").willReturn(jsonResponse("""
                {"error": "invalid_grant", "error_description": "token-body-marker"}
                """, 400)));
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
             .statusCode(502);

        List<String> logged = logs.records().stream()
                .flatMap(record -> loggedText(record).stream())
                .toList();
        assertThat(logged, everyItem(not(containsString("token-body-marker"))));
        assertThat(spanText(traceId), everyItem(not(containsString("token-body-marker"))));
    }

    @Test
    void malformedRequestValueIsNotTraced() {
        String traceId = TraceId.fromLongs(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong());

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .header("traceparent", "00-%s-00f067aa0ba902b7-01".formatted(traceId))
          .body("""
                {"type": "ONBOARD", "requestedBy": "alice.admin@corp.com", "firstName": "Jane", "lastName": "Doe",
                 "email": "jane.doe@corp.com", "department": "Finance", "startDate": "Jane-Doe",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);

        assertThat(spanText(traceId), everyItem(not(containsString("Jane-Doe"))));
    }

    /**
     * The attribute values of every span in the trace, and of their events, once its server span has finished.
     */
    private List<String> spanText(String traceId) {
        return await().until(() -> spans.getFinishedSpanItems().stream()
                .filter(span -> span.getTraceId().equals(traceId))
                .toList(), trace -> trace.stream().anyMatch(span -> span.getKind() == SpanKind.SERVER))
                .stream()
                .flatMap(span -> Stream.concat(Stream.of(span.getAttributes()),
                        span.getEvents().stream().map(EventData::getAttributes)))
                .flatMap(attributes -> attributes.asMap().values().stream())
                .map(String::valueOf)
                .toList();
    }

    /**
     * The message, parameters, thrown messages through the cause chain, and MDC values of a record.
     */
    private static List<String> loggedText(ExtLogRecord record) {
        List<String> text = new ArrayList<>();
        text.add(record.getMessage());
        text.add(record.getFormattedMessage());
        if (record.getParameters() != null) {
            Arrays.stream(record.getParameters()).map(String::valueOf).forEach(text::add);
        }
        for (Throwable t = record.getThrown(); t != null; t = t.getCause()) {
            text.add(String.valueOf(t.getMessage()));
        }
        record.getMdcCopy().values().stream().map(String::valueOf).forEach(text::add);
        return text;
    }

    /**
     * The application's own ERROR records, so that an unrelated one from WireMock or a library doesn't count.
     */
    private List<ExtLogRecord> errorRecords() {
        return logs.records().stream()
                .filter(record -> record.getLoggerName() != null && record.getLoggerName().startsWith("dev.abbah."))
                .filter(record -> record.getLevel().intValue() == Level.ERROR.intValue())
                .toList();
    }

    /**
     * The types and messages of the record's thrown exception and of every cause under it.
     */
    private static String causeChain(ExtLogRecord record) {
        List<String> chain = new ArrayList<>();
        for (Throwable t = record.getThrown(); t != null; t = t.getCause()) {
            chain.add(t.getClass().getName() + ": " + t.getMessage());
        }
        return String.join("\n", chain);
    }
}
