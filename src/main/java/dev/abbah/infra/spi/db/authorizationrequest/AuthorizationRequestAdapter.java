package dev.abbah.infra.spi.db.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Submitted;
import dev.abbah.domain.authorizationrequest.AuthorizationRequestPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class AuthorizationRequestAdapter implements AuthorizationRequestPort {

    private final AuthorizationRequestEntityMapper mapper;

    public AuthorizationRequestAdapter(AuthorizationRequestEntityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Submitted submitted) {
        mapper.toEntity(submitted).persist();
    }
}
