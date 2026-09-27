package dev.abbah.domain.authorizationrequest;

import java.time.LocalDate;
import java.util.List;

/**
 * A sealed interface rather than a record: the request variants share few fields,
 * and a sealed hierarchy keeps every switch over them exhaustive.
 */
public sealed interface AuthorizationRequest permits AuthorizationRequest.Grant, AuthorizationRequest.Revoke, AuthorizationRequest.Onboard {

    String requestedBy();

    List<String> authorizations();

    Type type();

    enum Type { GRANT, REVOKE, ONBOARD }

    record Grant(String requestedBy, String employeeId, List<String> authorizations) implements AuthorizationRequest {
        @Override
        public Type type() {
            return Type.GRANT;
        }
    }

    record Revoke(String requestedBy, String employeeId, List<String> authorizations) implements AuthorizationRequest {
        @Override
        public Type type() {
            return Type.REVOKE;
        }
    }

    record Onboard(String requestedBy, String firstName, String lastName, String email, String department,
                   LocalDate startDate, List<String> authorizations) implements AuthorizationRequest {
        @Override
        public Type type() {
            return Type.ONBOARD;
        }
    }

    record Message(String subject, String description) {
    }

    record Submitted(AuthorizationRequest request, Message message, String reference) {
    }
}
