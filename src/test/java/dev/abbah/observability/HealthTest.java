package dev.abbah.observability;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.abbah.infra.spi.rest.salesforce.InjectSalesforceStub;
import dev.abbah.infra.spi.rest.salesforce.SalesforceStub;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@WithTestResource(SalesforceStub.class)
class HealthTest {

    private static final String APEX_PATH = "/services/apexrest/authorization-requests/v1";

    @InjectSalesforceStub
    WireMockServer salesforce;

    @BeforeEach
    void resetSalesforce() {
        SalesforceStub.reset(salesforce);
    }

    @Test
    void runningApplicationIsLive() {
        given()
          .when().get("/q/health/live")
          .then()
             .statusCode(200)
             .body("status", is("UP"));
    }

    @Test
    void applicationIsReadyWithTheDatabaseUp() {
        given()
          .when().get("/q/health/ready")
          .then()
             .statusCode(200)
             .body("status", is("UP"))
             .body("checks.name", hasItem(containsStringIgnoringCase("database")));
    }

    @Test
    void salesforceUnavailabilityDoesNotAffectReadiness() {
        salesforce.stubFor(post(APEX_PATH).willReturn(aResponse().withStatus(503)));

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

        given()
          .when().get("/q/health/ready")
          .then()
             .statusCode(200)
             .body("status", is("UP"))
             .body("checks.name", everyItem(not(containsStringIgnoringCase("salesforce"))));
    }
}
