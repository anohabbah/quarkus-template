package dev.abbah.domain.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Message;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Submitted;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class AuthorizationRequestUsecase {

    private final AuthorizationRequestPort port;
    private final AuthorizationRequestRendererPort rendererPort;

    public AuthorizationRequestUsecase(AuthorizationRequestPort port, AuthorizationRequestRendererPort rendererPort) {
        this.port = port;
        this.rendererPort = rendererPort;
    }

    public Submitted submit(UUID requestId, AuthorizationRequest request) {
        Message message = rendererPort.render(request);
        String reference = port.submit(requestId, request, message);
        return new Submitted(request, message, reference);
    }
}
