package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AuthorizationRequestPayloadMapper {

    @Mapping(target = "type", expression = "java(request.type().name())")
    @Mapping(target = "requesterEmail", expression = "java(request.requestedBy())")
    @Mapping(target = "subject", source = "message.subject")
    @Mapping(target = "description", source = "message.description")
    AuthorizationRequestPayload.Request toRequest(AuthorizationRequest request, Message message);
}
