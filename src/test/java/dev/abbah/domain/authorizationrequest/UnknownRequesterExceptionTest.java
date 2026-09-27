package dev.abbah.domain.authorizationrequest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnknownRequesterExceptionTest {

    @Test
    void messageMasksTheRequesterEmail() {
        var exception = new UnknownRequesterException("alice.admin@corp.com", null);

        assertEquals("Unknown requester: a******n@corp.com", exception.getMessage());
    }

    @Test
    void messageHidesAShortLocalPartEntirely() {
        var exception = new UnknownRequesterException("ab@corp.com", null);

        assertEquals("Unknown requester: ******@corp.com", exception.getMessage());
    }
}
