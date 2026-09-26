package dev.abbah.infra.spi.template.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Grant;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Message;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Onboard;
import dev.abbah.domain.authorizationrequest.AuthorizationRequest.Revoke;
import dev.abbah.domain.authorizationrequest.AuthorizationRequestRendererPort;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuthorizationRequestRenderer implements AuthorizationRequestRendererPort {

    // Qute binds template$fragment method names to template fragments.
    @SuppressWarnings("java:S100")
    @CheckedTemplate
    static class Templates {
        private Templates() {
        }

        static native TemplateInstance grant$subject(Grant request);

        static native TemplateInstance grant$description(Grant request);

        static native TemplateInstance revoke$subject(Revoke request);

        static native TemplateInstance revoke$description(Revoke request);

        static native TemplateInstance onboard$subject(Onboard request);

        static native TemplateInstance onboard$description(Onboard request);
    }

    @Override
    public Message render(AuthorizationRequest request) {
        return switch (request) {
            case Grant grant -> message(Templates.grant$subject(grant), Templates.grant$description(grant));
            case Revoke revoke -> message(Templates.revoke$subject(revoke), Templates.revoke$description(revoke));
            case Onboard onboard -> message(Templates.onboard$subject(onboard), Templates.onboard$description(onboard));
        };
    }

    private static Message message(TemplateInstance subject, TemplateInstance description) {
        return new Message(subject.render().strip(), description.render().strip());
    }
}
