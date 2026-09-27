package dev.abbah.infra.spi.rest.salesforce;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Stands in for Salesforce at the network boundary: a WireMock server that issues OAuth tokens
 * and receives the Apex REST calls. Tests stub the Apex responses they need.
 */
public class SalesforceStub implements QuarkusTestResourceLifecycleManager {

    public static final String ACCESS_TOKEN = "stub-access-token";

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        reset(server);
        return Map.of(
                "quarkus.rest-client.salesforce.url", server.baseUrl(),
                "quarkus.oidc-client.auth-server-url", server.baseUrl(),
                "quarkus.oidc-client.client-id", "stub-client-id",
                "quarkus.oidc-client.credentials.secret", "stub-client-secret");
    }

    /**
     * Forgets every stub and received request, then stubs the token endpoint again.
     */
    public static void reset(WireMockServer server) {
        server.resetAll();
        // Like Salesforce, the token response carries no expires_in.
        server.stubFor(post("/services/oauth2/token")
                .willReturn(okJson("""
                        {"access_token": "%s", "token_type": "Bearer"}
                        """.formatted(ACCESS_TOKEN))));
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(server,
                new TestInjector.AnnotatedAndMatchesType(InjectSalesforceStub.class, WireMockServer.class));
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
