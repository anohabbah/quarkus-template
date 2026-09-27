package dev.abbah.infra.api.rest.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Submitted;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper
public interface AuthorizationRequestDtoMapper {

    @SubclassMapping(source = AuthorizationRequestDto.Grant.class, target = AuthorizationRequest.Grant.class)
    @SubclassMapping(source = AuthorizationRequestDto.Revoke.class, target = AuthorizationRequest.Revoke.class)
    @SubclassMapping(source = AuthorizationRequestDto.Onboard.class, target = AuthorizationRequest.Onboard.class)
    @BeanMapping(subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
    AuthorizationRequest toDomain(AuthorizationRequestDto dto);

    @Mapping(target = "caseNumber", source = "reference")
    @Mapping(target = "type", expression = "java(submitted.request().type().name())")
    @Mapping(target = "subject", source = "message.subject")
    @Mapping(target = "description", source = "message.description")
    AuthorizationRequestDto.Response toResponse(Submitted submitted);
}
