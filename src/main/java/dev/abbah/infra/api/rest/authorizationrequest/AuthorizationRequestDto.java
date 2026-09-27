package dev.abbah.infra.api.rest.authorizationrequest;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * A sealed interface rather than a record: Jackson picks the request variant from the
 * {@code type} property, and each variant is a record.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AuthorizationRequestDto.Grant.class, name = "GRANT"),
        @JsonSubTypes.Type(value = AuthorizationRequestDto.Revoke.class, name = "REVOKE"),
        @JsonSubTypes.Type(value = AuthorizationRequestDto.Onboard.class, name = "ONBOARD"),
})
public sealed interface AuthorizationRequestDto permits AuthorizationRequestDto.Grant, AuthorizationRequestDto.Revoke, AuthorizationRequestDto.Onboard {

    record Grant(@NotBlank @Email String requestedBy, @NotBlank String employeeId,
                 @NotEmpty List<@NotBlank String> authorizations) implements AuthorizationRequestDto {
    }

    record Revoke(@NotBlank @Email String requestedBy, @NotBlank String employeeId,
                  @NotEmpty List<@NotBlank String> authorizations) implements AuthorizationRequestDto {
    }

    record Onboard(@NotBlank @Email String requestedBy, @NotBlank String firstName, @NotBlank String lastName,
                   @NotBlank @Email String email, @NotBlank String department, @NotNull LocalDate startDate,
                   @NotEmpty List<@NotBlank String> authorizations) implements AuthorizationRequestDto {
    }

    record Response(String caseNumber, String type, String subject, String description) {
    }
}
