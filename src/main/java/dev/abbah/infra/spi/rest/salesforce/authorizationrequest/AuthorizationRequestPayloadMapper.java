package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Mapper
public interface AuthorizationRequestPayloadMapper {

    @Mapping(target = "requestHash", expression = "java(requestHash(request.type().name(), request.requestedBy(), message.subject(), message.description()))")
    @Mapping(target = "type", expression = "java(request.type().name())")
    @Mapping(target = "requesterEmail", expression = "java(request.requestedBy())")
    @Mapping(target = "subject", source = "message.subject")
    @Mapping(target = "description", source = "message.description")
    AuthorizationRequestPayload.Request toRequest(UUID requestId, AuthorizationRequest request, Message message);

    /**
     * Lowercase hex SHA-256 of the fields, each UTF-8 encoded and prefixed with its byte length,
     * so that no two different sequences of fields share an input.
     */
    default String requestHash(String... fields) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (String field : fields) {
            byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
            digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
