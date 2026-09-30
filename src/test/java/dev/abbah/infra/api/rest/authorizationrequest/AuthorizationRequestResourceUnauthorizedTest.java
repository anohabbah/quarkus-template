package dev.abbah.infra.api.rest.authorizationrequest;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
// Its own profile restarts the application, so the fresh token doesn't leak into other tests.
@TestProfile(AuthorizationRequestResourceUnauthorizedTest.FreshStart.class)
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestResourceUnauthorizedTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";
    private static final String FRESH_TOKEN = "fresh-token";

    public static class FreshStart implements QuarkusTestProfile {
    }

    @InjectSalesforceStub
    WireMockServer salesforce;

    @BeforeEach
    void salesforceRevokesTheFirstToken() {
        SalesforceStub.reset(salesforce);
        salesforce.stubFor(post(APEX_PATH).inScenario("revoked session")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("revoked"));
        salesforce.stubFor(post("/services/oauth2/token").inScenario("revoked session")
                .whenScenarioStateIs("revoked")
                .willReturn(okJson("""
                        {"access_token": "%s", "token_type": "Bearer"}
                        """.formatted(FRESH_TOKEN))));
        salesforce.stubFor(post(APEX_PATH)
                .withHeader("Authorization", equalTo("Bearer " + FRESH_TOKEN))
                .willReturn(jsonResponse("""
                        {"caseId": "500x", "caseNumber": "00012345"}
                        """, 201)));
    }

    @Test
    void unauthorizedCallIsRetriedWithAFreshToken() {
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

        salesforce.verify(2, postRequestedFor(urlEqualTo(APEX_PATH)));
        salesforce.verify(1, postRequestedFor(urlEqualTo(APEX_PATH))
                .withHeader("Authorization", equalTo("Bearer " + FRESH_TOKEN)));
    }
}
