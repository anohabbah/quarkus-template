package dev.abbah.infra.spi.db.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AuthorizationRequestStorageTest {

    @Test
    @Transactional
    void grantRequestIsStored() {
        ExtractableResponse<Response> response = submit("""
                {"type": "GRANT", "requestedBy": "alice.admin", "employeeId": "E1234",
                 "authorizations": ["READ_PAYROLL", "EDIT_TIMESHEETS"]}
                """);

        AuthorizationRequestEntity entity = AuthorizationRequestEntity.findById(UUID.fromString(response.path("id")));

        assertNotNull(entity);
        assertEquals(AuthorizationRequest.Type.GRANT, entity.type);
        assertEquals("alice.admin", entity.requestedBy);
        assertEquals("E1234", entity.employeeId);
        assertEquals(List.of("READ_PAYROLL", "EDIT_TIMESHEETS"), entity.authorizations);
        assertNotNull(entity.requestedAt);
        assertEquals(response.path("subject"), entity.subject);
        assertEquals(response.path("description"), entity.description);
    }

    @Test
    @Transactional
    void onboardingRequestIsStored() {
        ExtractableResponse<Response> response = submit("""
                {"type": "ONBOARD", "requestedBy": "alice.admin", "firstName": "Jane", "lastName": "Doe",
                 "email": "jane.doe@corp.com", "department": "Finance", "startDate": "2026-10-01",
                 "authorizations": ["READ_PAYROLL"]}
                """);

        AuthorizationRequestEntity entity = AuthorizationRequestEntity.findById(UUID.fromString(response.path("id")));

        assertNotNull(entity);
        assertEquals(AuthorizationRequest.Type.ONBOARD, entity.type);
        assertEquals("alice.admin", entity.requestedBy);
        assertEquals("Jane", entity.firstName);
        assertEquals("Doe", entity.lastName);
        assertEquals("jane.doe@corp.com", entity.email);
        assertEquals("Finance", entity.department);
        assertEquals(LocalDate.of(2026, Month.OCTOBER, 1), entity.startDate);
        assertEquals(List.of("READ_PAYROLL"), entity.authorizations);
        assertNotNull(entity.requestedAt);
        assertEquals(response.path("subject"), entity.subject);
        assertEquals(response.path("description"), entity.description);
    }

    @Test
    @Transactional
    void rejectedRequestIsNotStored() {
        long countBefore = AuthorizationRequestEntity.count();

        given()
          .contentType(ContentType.JSON)
          .body("""
                {"type": "GRANT", "requestedBy": "alice.admin", "authorizations": ["READ_PAYROLL"]}
                """)
          .when().post("/authorization-requests")
          .then()
             .statusCode(400);

        assertEquals(countBefore, AuthorizationRequestEntity.count());
    }

    private static ExtractableResponse<Response> submit(String body) {
        return given()
          .contentType(ContentType.JSON)
          .body(body)
          .when().post("/authorization-requests")
          .then()
             .statusCode(201)
             .extract();
    }
}
