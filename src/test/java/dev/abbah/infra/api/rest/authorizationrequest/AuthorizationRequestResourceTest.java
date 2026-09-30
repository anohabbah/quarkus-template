package dev.abbah.infra.api.rest.authorizationrequest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import dev.abbah.infra.spi.rest.salesforce.InjectSalesforceStub;
import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestResourceTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";

    @InjectSalesforceStub
    WireMockServer salesforce;

    @BeforeEach
    void resetSalesforce() {
        SalesforceStub.reset(salesforce);
    }

    private void salesforceFilesCase() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"caseId": "500x", "caseNumber": "00012345"}
                """, 201)));
    }

    @Test
    void validGrantRequestIsAccepted() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL", "EDIT_TIMESHEETS"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("caseNumber", is("00012345"))
             .body("type", is("GRANT"))
             .body("subject", is("Authorization grant request"))
             .body("description", is("""
                     Requested by: alice.admin@corp.com
                     Employee ID: E1234
                     Authorizations:
                     - READ_PAYROLL
                     - EDIT_TIMESHEETS"""));
    }

    @Test
    void grantRequestIsSentToSalesforce() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70")
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL", "EDIT_TIMESHEETS"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("description", is("""
                     Requested by: alice.admin@corp.com
                     Employee ID: E1234
                     Authorizations:
                     - READ_PAYROLL
                     - EDIT_TIMESHEETS"""));

        salesforce.verify(1, postRequestedFor(urlEqualTo(APEX_PATH))
                .withHeader("Authorization", equalTo("Bearer " + SalesforceStub.ACCESS_TOKEN))
                .withRequestBody(equalToJson("""
                        {"requestId": "7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70",
                         "requestHash": "${json-unit.any-string}",
                         "type": "GRANT", "requesterEmail": "alice.admin@corp.com",
                         "subject": "Authorization grant request",
                         "description": "Requested by: alice.admin@corp.com\\nEmployee ID: E1234\\nAuthorizations:\\n- READ_PAYROLL\\n- EDIT_TIMESHEETS"}
                        """)));
    }

    @Test
    void identicalRequestsCarryTheSameHash() {
        salesforceFilesCase();
        String grant = """
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """;

        for (int i = 0; i < 2; i++) {
            given()
              .contentType(ContentType.JSON)
              .header("Idempotency-Key", UUID.randomUUID())
              .body(grant)
              .when().post("/authorization-requests")
              .then()
                 .statusCode(201);
        }

        List<String> hashes = requestHashesReceived();
        assertThat(hashes, hasSize(2));
        assertThat(hashes.get(0), not(blankOrNullString()));
        assertThat(hashes.get(1), is(hashes.get(0)));
    }

    @Test
    void differentRequestsCarryDifferentHashes() {
        salesforceFilesCase();

        for (String employeeId : List.of("E1234", "E5678")) {
            given()
              .contentType(ContentType.JSON)
              .header("Idempotency-Key", UUID.randomUUID())
              .body("""
                    {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "%s",
                     "authorizations": ["READ_PAYROLL"]}
                    """.formatted(employeeId))
              .when().post("/authorization-requests")
              .then()
                 .statusCode(201);
        }

        List<String> hashes = requestHashesReceived();
        assertThat(hashes, hasSize(2));
        assertThat(hashes.get(1), not(is(hashes.get(0))));
    }

    private List<String> requestHashesReceived() {
        return salesforce.getAllServeEvents().stream()
                .map(ServeEvent::getRequest)
                .filter(request -> request.getUrl().equals(APEX_PATH))
                .map(request -> JsonPath.from(request.getBodyAsString()).getString("requestHash"))
                .toList();
    }

    @Test
    void validRevokeRequestIsAccepted() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "REVOKE", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("caseNumber", is("00012345"))
             .body("type", is("REVOKE"))
             .body("subject", is("Authorization revocation request"))
             .body("description", is("""
                     Requested by: alice.admin@corp.com
                     Employee ID: E1234
                     Authorizations:
                     - READ_PAYROLL"""));
    }

    @Test
    void validOnboardingRequestIsAccepted() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "ONBOARD", "requestedBy": "alice.admin@corp.com", "firstName": "Jane", "lastName": "Doe",
                 "email": "jane.doe@corp.com", "department": "Finance", "startDate": "2026-10-01",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("caseNumber", is("00012345"))
             .body("type", is("ONBOARD"))
             .body("subject", is("Employee onboarding request"))
             .body("description", is("""
                     Requested by: alice.admin@corp.com
                     Employee: Jane Doe <jane.doe@corp.com>
                     Department: Finance
                     Start date: 2026-10-01
                     Authorizations:
                     - READ_PAYROLL"""));
    }

    @Test
    void onboardingRequestIsSentToSalesforce() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "0b8e4d2c-6a1f-4c3e-8d7b-5e9f1a2c3b4d")
          .body("""
                {"type": "ONBOARD", "requestedBy": "alice.admin@corp.com", "firstName": "Jane", "lastName": "Doe",
                 "email": "jane.doe@corp.com", "department": "Finance", "startDate": "2026-10-01",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("subject", is("Employee onboarding request"))
             .body("description", is("""
                     Requested by: alice.admin@corp.com
                     Employee: Jane Doe <jane.doe@corp.com>
                     Department: Finance
                     Start date: 2026-10-01
                     Authorizations:
                     - READ_PAYROLL"""));

        salesforce.verify(1, postRequestedFor(urlEqualTo(APEX_PATH))
                .withRequestBody(equalToJson("""
                        {"requestId": "0b8e4d2c-6a1f-4c3e-8d7b-5e9f1a2c3b4d",
                         "requestHash": "${json-unit.any-string}",
                         "type": "ONBOARD", "requesterEmail": "alice.admin@corp.com",
                         "subject": "Employee onboarding request",
                         "description": "Requested by: alice.admin@corp.com\\nEmployee: Jane Doe <jane.doe@corp.com>\\nDepartment: Finance\\nStart date: 2026-10-01\\nAuthorizations:\\n- READ_PAYROLL"}
                        """)));
    }

    @Test
    void retriedRequestReturnsTheOriginalCase() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"caseId": "500x", "caseNumber": "00012345"}
                """, 200)));

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70")
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("caseNumber", is("00012345"))
             .body("type", is("GRANT"))
             .body("subject", is("Authorization grant request"))
             .body("description", is("""
                     Requested by: alice.admin@corp.com
                     Employee ID: E1234
                     Authorizations:
                     - READ_PAYROLL"""));
    }

    @Test
    void unknownRequesterIsRejected() {
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
    }

    @Test
    void reusedIdempotencyKeyIsRejected() {
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
    }

    @Test
    void salesforceServerErrorIsReportedAsBadGateway() {
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
    }

    @Test
    void salesforceRejectingTheCallIsReportedAsBadGateway() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"errorCode": "INVALID_REQUEST", "message": "x"}
                """, 400)));

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
    }

    @Test
    void salesforceUnprocessableWithOtherErrorCodeIsReportedAsBadGateway() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"errorCode": "OTHER"}
                """, 422)));

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
    }

    @Test
    void salesforceConflictWithOtherErrorCodeIsReportedAsBadGateway() {
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"errorCode": "OTHER"}
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
             .statusCode(502);
    }

    @Test
    void salesforceResponseWithoutCaseNumberIsReportedAsBadGateway() {
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

        salesforce.verify(1, postRequestedFor(urlEqualTo(APEX_PATH)));
    }

    @Test
    void salesforceResponseWithoutBodyIsReportedAsBadGateway() {
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(204)));

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
    }

    @Test
    void requestWithoutIdempotencyKeyIsRejected() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);

        salesforce.verify(0, postRequestedFor(urlEqualTo(APEX_PATH)));
    }

    @Test
    void idempotencyKeyThatIsNotAUuidIsRejected() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "retry-1")
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);

        salesforce.verify(0, postRequestedFor(urlEqualTo(APEX_PATH)));
    }

    @Test
    void idempotencyKeyThatIsNotACanonicalUuidIsRejected() {
        salesforceFilesCase();

        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "1-1-1-1-1")
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);

        salesforce.verify(0, postRequestedFor(urlEqualTo(APEX_PATH)));
    }

    @Test
    void unknownTypeIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "SUSPEND", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void missingTypeIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void grantWithoutEmployeeIdIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);

        salesforce.verify(0, postRequestedFor(urlEqualTo(APEX_PATH)));
    }

    @Test
    void grantWithoutAuthorizationsIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234"}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void revokeWithoutEmployeeIdIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "REVOKE", "requestedBy": "alice.admin@corp.com", "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void revokeWithEmptyAuthorizationsIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "REVOKE", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234", "authorizations": []}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void onboardingWithInvalidEmailIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "ONBOARD", "requestedBy": "alice.admin@corp.com", "firstName": "Jane", "lastName": "Doe",
                 "email": "not-an-email", "department": "Finance", "startDate": "2026-10-01",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void requesterThatIsNotAnEmailIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);

        salesforce.verify(0, postRequestedFor(urlEqualTo(APEX_PATH)));
    }
}
