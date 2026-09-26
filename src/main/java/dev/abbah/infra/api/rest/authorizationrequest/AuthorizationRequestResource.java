package dev.abbah.infra.api.rest.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequestUsecase;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;

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
}
