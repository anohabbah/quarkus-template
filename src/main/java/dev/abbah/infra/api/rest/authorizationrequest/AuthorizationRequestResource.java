package dev.abbah.infra.api.rest.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequestUsecase;
import dev.abbah.domain.authorizationrequest.SubmissionFailedException;
import dev.abbah.domain.authorizationrequest.UnknownRequesterException;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/authorization-requests")
public class AuthorizationRequestResource {

    private final AuthorizationRequestUsecase usecase;
    private final AuthorizationRequestDtoMapper mapper;

    public AuthorizationRequestResource(AuthorizationRequestUsecase usecase, AuthorizationRequestDtoMapper mapper) {
        this.usecase = usecase;
        this.mapper = mapper;
    }

    @POST
    @ResponseStatus(201)
    public AuthorizationRequestDto.Response submit(@Valid AuthorizationRequestDto request) {
        return mapper.toResponse(usecase.submit(mapper.toDomain(request)));
    }

    @ServerExceptionMapper
    public Response unknownRequester(UnknownRequesterException e) {
        return Response.status(422).build();
    }

    @ServerExceptionMapper
    public Response submissionFailed(SubmissionFailedException e) {
        return Response.status(Response.Status.BAD_GATEWAY).build();
    }
}
