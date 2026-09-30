package dev.abbah.infra.api.rest.authorizationrequest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import dev.abbah.infra.spi.rest.salesforce.InjectSalesforceStub;
import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
// Its own profile restarts the application, so no token is cached from other tests.
@TestProfile(AuthorizationRequestResourceTokenUnreachableTest.FreshStart.class)
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestResourceTokenUnreachableTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";

    public static class FreshStart implements QuarkusTestProfile {
    }

    @InjectSalesforceStub
    WireMockServer salesforce;

    @BeforeEach
    void tokenEndpointDropsTheFirstConnection() {
        SalesforceStub.reset(salesforce);
        salesforce.stubFor(post("/services/oauth2/token").inScenario("dropped connection")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("reachable"));
        salesforce.stubFor(post("/services/oauth2/token").inScenario("dropped connection")
                .whenScenarioStateIs("reachable")
                .willReturn(okJson("""
                        {"access_token": "%s", "token_type": "Bearer"}
                        """.formatted(SalesforceStub.ACCESS_TOKEN))));
        salesforce.stubFor(post(APEX_PATH).willReturn(jsonResponse("""
                {"caseId": "500x", "caseNumber": "00012345"}
                """, 201)));
    }

    @Test
    void tokenEndpointDroppingTheConnectionIsRetried() {
        given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", UUID.randomUUID())
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("caseNumber", is("00012345"));
    }
}
