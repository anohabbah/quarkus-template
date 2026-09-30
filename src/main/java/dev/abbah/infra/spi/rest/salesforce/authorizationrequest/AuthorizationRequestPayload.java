package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import java.util.UUID;

/**
 * The records exchanged with the Salesforce authorization-requests Apex REST endpoint.
 */
public interface AuthorizationRequestPayload {

    record Request(UUID requestId, String requestHash, String type, String requesterEmail, String subject, String description) {
    }

    record Response(String caseId, String caseNumber) {
    }

    record Error(String errorCode, String message) {
    }
}
