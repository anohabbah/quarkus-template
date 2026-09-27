package dev.abbah.infra.spi.rest.salesforce;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code WireMockServer} test field that {@link SalesforceStub} fills with its running server.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectSalesforceStub {
}
