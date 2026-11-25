package io.quarkiverse.cxf.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

/**
 * Marks an injection point for injecting a WebService client.
 */
@Qualifier
@Target({ FIELD, PARAMETER, METHOD })
@Retention(RUNTIME)
@Documented
public @interface CXFClient {
    /**
     * The client key, such as {@code myClient} present in application configuration, e.g. {@code quarkus.cxf.client.myClient}.
     *
     * @return the client key, such as {@code myClient} present in application configuration, e.g.
     *         {@code quarkus.cxf.client.myClient}
     */
    @Nonbinding
    String value() default "";

    /**
     * The URL of the remote service endpoint the client injected at this injection point sends its requests to.
     * <p>
     * {@code quarkus.cxf.client."client-name".client-endpoint-url} takes precedence over this attribute,
     * so that the URL can be changed per environment without recompiling the application.
     * This attribute is not evaluated at all, if {@code client-endpoint-url} is set for the given client.
     * <p>
     * The value may contain property expressions, such as
     * <code>${calculator.baseUri}/calculator-ws/CalculatorService</code>.
     * The keys are looked up in the application configuration.
     * The syntax is the same as in {@code application.properties}, except that secret handler expressions, such as
     * <code>${handler::value}</code>, are not supported.
     * <p>
     * All injection points of the given client must specify the same value of this attribute,
     * otherwise the client fails when it is created.
     * The values are compared before their property expressions are expanded.
     * An attribute that is not specified is compared as an empty string.
     * <p>
     * If this attribute is specified on an injection point without a client name
     * and if there is no {@code quarkus.cxf.client."client-name"} having {@code service-interface} set to the service
     * endpoint interface of the client, then the fully qualified class name of the service endpoint interface is used as
     * the client name.
     *
     * @return the URL of the remote service endpoint, possibly containing property expressions;
     *         or an empty string if no URL is specified at this injection point
     * @since 4.1.0
     */
    @Nonbinding
    String url() default "";
}
