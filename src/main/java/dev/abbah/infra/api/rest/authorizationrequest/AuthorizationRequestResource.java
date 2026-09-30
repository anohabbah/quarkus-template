package dev.abbah.infra.api.rest.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequestUsecase;
import dev.abbah.domain.authorizationrequest.RequestIdReusedException;
import dev.abbah.domain.authorizationrequest.SubmissionFailedException;
import dev.abbah.domain.authorizationrequest.UnknownRequesterException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.UUID;

@Path("/authorization-requests")
public class AuthorizationRequestResource {

    /** {@link UUID#fromString} also accepts shortened forms such as {@code 1-1-1-1-1}. */
    private static final String CANONICAL_UUID = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final AuthorizationRequestUsecase usecase;
    private final AuthorizationRequestDtoMapper mapper;

    public AuthorizationRequestResource(AuthorizationRequestUsecase usecase, AuthorizationRequestDtoMapper mapper) {
        this.usecase = usecase;
        this.mapper = mapper;
    }

    @POST
    @ResponseStatus(201)
    public AuthorizationRequestDto.Response submit(@HeaderParam("Idempotency-Key") @NotNull @Pattern(regexp = CANONICAL_UUID) String requestId,
                                                   @Valid AuthorizationRequestDto request) {
        return mapper.toResponse(usecase.submit(UUID.fromString(requestId), mapper.toDomain(request)));
    }

    @ServerExceptionMapper
    public Response unknownRequester(UnknownRequesterException e) {
        return Response.status(422).build();
    }

    @ServerExceptionMapper
    public Response requestIdReused(RequestIdReusedException e) {
        return Response.status(Response.Status.CONFLICT).build();
    }

    @ServerExceptionMapper
    public Response submissionFailed(SubmissionFailedException e) {
        return Response.status(Response.Status.BAD_GATEWAY).build();
    }
}
