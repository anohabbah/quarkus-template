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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;

@QuarkusTest
// Its own profile restarts the application, so that no token is cached.
@TestProfile(AuthorizationRequestResourcePersistentUnauthorizedTest.FreshStart.class)
@WithTestResource(SalesforceStub.class)
class AuthorizationRequestResourcePersistentUnauthorizedTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";

    public static class FreshStart implements QuarkusTestProfile {
    }

    @InjectSalesforceStub
    WireMockServer salesforce;

    @BeforeEach
    void salesforceRejectsEveryToken() {
        SalesforceStub.reset(salesforce);
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(401)));
    }

    @Test
    void persistentUnauthorizedAnswerIsReportedAsBadGateway() {
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

        salesforce.verify(3, postRequestedFor(urlEqualTo(APEX_PATH)));
        // Only the first 401 gets a new token: the filter doesn't detect a 401 on the retry that fetched it.
        salesforce.verify(2, postRequestedFor(urlEqualTo("/services/oauth2/token")));
    }
}
