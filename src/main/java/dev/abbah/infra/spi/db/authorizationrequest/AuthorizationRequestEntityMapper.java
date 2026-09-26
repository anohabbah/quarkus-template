package dev.abbah.infra.spi.db.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Grant;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Onboard;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Revoke;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Submitted;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Mapper
public interface AuthorizationRequestEntityMapper {

    default AuthorizationRequestEntity toEntity(Submitted submitted) {
        return switch (submitted.request()) {
            case Grant grant -> toEntity(submitted, grant);
            case Revoke revoke -> toEntity(submitted, revoke);
            case Onboard onboard -> toEntity(submitted, onboard);
        };
    }

    @FromSubmitted
    AuthorizationRequestEntity toEntity(Submitted submitted, Grant grant);

    @FromSubmitted
    AuthorizationRequestEntity toEntity(Submitted submitted, Revoke revoke);

    @FromSubmitted
    AuthorizationRequestEntity toEntity(Submitted submitted, Onboard onboard);

    /**
     * Mappings shared by every variant: the type and the generated message come from {@code submitted},
     * and the variant's own fields are mapped by name.
     */
    @Retention(RetentionPolicy.CLASS)
    @Mapping(target = "type", expression = "java(submitted.request().type())")
    @Mapping(target = "subject", source = "submitted.message.subject")
    @Mapping(target = "description", source = "submitted.message.description")
    @interface FromSubmitted {
    }
}
