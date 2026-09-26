package dev.abbah.domain.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Message;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Submitted;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class AuthorizationRequestUsecase {

    private final AuthorizationRequestPort port;
    private final AuthorizationRequestRendererPort rendererPort;

    public AuthorizationRequestUsecase(AuthorizationRequestPort port, AuthorizationRequestRendererPort rendererPort) {
        this.port = port;
        this.rendererPort = rendererPort;
    }

    public Submitted submit(AuthorizationRequest request) {
        Message message = rendererPort.render(request);
        Submitted submitted = new Submitted(UUID.randomUUID(), Instant.now(), request, message);
        port.save(submitted);
        return submitted;
    }
}
