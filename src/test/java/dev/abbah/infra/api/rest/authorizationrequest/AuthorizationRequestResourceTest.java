package dev.abbah.infra.api.rest.authorizationrequest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class AuthorizationRequestResourceTest {

    @Test
    void validGrantRequestIsAccepted() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL", "EDIT_TIMESHEETS"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("id", notNullValue())
             .body("type", is("GRANT"))
             .body("subject", is("Authorization grant request"))
             .body("description", is("""
                     Requested by: alice.admin
                     Employee ID: E1234
                     Authorizations:
                     - READ_PAYROLL
                     - EDIT_TIMESHEETS"""));
    }

    @Test
    void validRevokeRequestIsAccepted() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "REVOKE", "requestedBy": "alice.admin", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("id", notNullValue())
             .body("type", is("REVOKE"))
             .body("subject", is("Authorization revocation request"))
             .body("description", is("""
                     Requested by: alice.admin
                     Employee ID: E1234
                     Authorizations:
                     - READ_PAYROLL"""));
    }

    @Test
    void validOnboardingRequestIsAccepted() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "ONBOARD", "requestedBy": "alice.admin", "firstName": "Jane", "lastName": "Doe",
                 "email": "jane.doe@corp.com", "department": "Finance", "startDate": "2026-10-01",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .body("id", notNullValue())
             .body("type", is("ONBOARD"))
             .body("subject", is("Employee onboarding request"))
             .body("description", is("""
                     Requested by: alice.admin
                     Employee: Jane Doe <jane.doe@corp.com>
                     Department: Finance
                     Start date: 2026-10-01
                     Authorizations:
                     - READ_PAYROLL"""));
    }

    @Test
    void unknownTypeIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "SUSPEND", "requestedBy": "alice.admin", "employeeId": "E1234",
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
          .body("""
                {"requestedBy": "alice.admin", "employeeId": "E1234",
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
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin", "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void grantWithoutAuthorizationsIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin", "employeeId": "E1234"}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void revokeWithoutEmployeeIdIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "REVOKE", "requestedBy": "alice.admin", "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void revokeWithEmptyAuthorizationsIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "REVOKE", "requestedBy": "alice.admin", "employeeId": "E1234", "authorizations": []}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }

    @Test
    void onboardingWithInvalidEmailIsRejected() {
        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "ONBOARD", "requestedBy": "alice.admin", "firstName": "Jane", "lastName": "Doe",
                 "email": "not-an-email", "department": "Finance", "startDate": "2026-10-01",
                 "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);
    }
}
