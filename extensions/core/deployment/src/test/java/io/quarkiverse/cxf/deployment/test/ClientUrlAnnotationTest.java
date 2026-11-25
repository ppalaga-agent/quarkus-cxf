package io.quarkiverse.cxf.deployment.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.apache.cxf.frontend.ClientProxy;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.cxf.CXFClientInfo;
import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The URL of the remote service endpoint can be specified through {@link CXFClient#url()},
 * while {@code quarkus.cxf.client."client-name".client-endpoint-url} takes precedence over it.
 */
public class ClientUrlAnnotationTest {

    private static final String NAMESPACE = "http://test.deployment.cxf.quarkiverse.io/";
    private static final String SEI = HelloService.class.getName();
    /** The endpoint replying {@code Hello Joe} */
    private static final String HELLO_URL = "http://localhost:8081/services/hello";
    /** The endpoint replying {@code Hi Joe} */
    private static final String HI_URL = "http://localhost:8081/services/hi";

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            HelloService.class,
                            HelloServiceImpl.class,
                            HiServiceImpl.class,
                            UnnamedNoConfigService.class,
                            UnnamedConfigService.class,
                            UnnamedConfigUrlService.class,
                            UnnamedConflictService.class,
                            ConstructorInjectedBean.class)
                    .addAsResource(
                            new StringAsset(
                                    """
                                            test.services.url = http://localhost:8081/services
                                            test.hello.url = ${test.services.url}/hello
                                            test.profile.url = http://localhost:8081/services/hi
                                            %test.test.profile.url = http://localhost:8081/services/hello
                                            """),
                            "application.properties"))
            .overrideConfigKey("quarkus.cxf.endpoint.\"/hello\".implementor", HelloServiceImpl.class.getName())
            .overrideConfigKey("quarkus.cxf.endpoint.\"/hi\".implementor", HiServiceImpl.class.getName())

            /* service-interface is required for injecting CXFClientInfo */
            .overrideConfigKey("quarkus.cxf.client.literal.service-interface", SEI)

            .overrideConfigKey("quarkus.cxf.client.both.client-endpoint-url", HI_URL)
            .overrideConfigKey("quarkus.cxf.client.both.service-interface", SEI)

            .overrideConfigKey("quarkus.cxf.client.conflict.service-interface", SEI)
            .overrideConfigKey("quarkus.cxf.client.conflictOverridden.client-endpoint-url", HI_URL)

            .overrideConfigKey("quarkus.cxf.client.undefined.service-interface", SEI)
            .overrideConfigKey("quarkus.cxf.client.undefinedOverridden.client-endpoint-url", HI_URL)

            .overrideConfigKey("quarkus.cxf.client.unnamedConfig.service-interface", UnnamedConfigService.class.getName())
            .overrideConfigKey("quarkus.cxf.client.unnamedConfig.receive-timeout", "12345")

            .overrideConfigKey("quarkus.cxf.client.unnamedConfigUrl.client-endpoint-url", HI_URL)
            .overrideConfigKey("quarkus.cxf.client.unnamedConfigUrl.service-interface",
                    UnnamedConfigUrlService.class.getName());

    /* AC-1: A literal URL */
    @CXFClient(value = "literal", url = HELLO_URL)
    HelloService literal;

    /* AC-8: The same raw value on another injection point of the same client */
    @CXFClient(value = "literal", url = HELLO_URL)
    Instance<HelloService> literalInstance;

    /* AC-9: CXFClientInfo */
    @CXFClient(value = "literal", url = HELLO_URL)
    CXFClientInfo literalInfo;

    /* AC-3: client-endpoint-url takes precedence */
    @CXFClient(value = "both", url = HELLO_URL)
    HelloService both;

    @CXFClient(value = "both", url = HELLO_URL)
    CXFClientInfo bothInfo;

    /* AC-4: No URL at all */
    @CXFClient("neither")
    Instance<HelloService> neither;

    /* AC-7: Different URLs on the injection points of the same client */
    @CXFClient(value = "conflict", url = HELLO_URL)
    Instance<HelloService> conflictHello;

    @CXFClient(value = "conflict", url = HI_URL)
    Instance<HelloService> conflictHi;

    @CXFClient(value = "conflict", url = HELLO_URL)
    Instance<CXFClientInfo> conflictInfo;

    /* AC-7: An attribute that is not specified is compared as an empty string */
    @CXFClient(value = "conflictEmpty", url = HELLO_URL)
    Instance<HelloService> conflictEmptyHello;

    @CXFClient("conflictEmpty")
    Instance<HelloService> conflictEmpty;

    /* AC-8: Different raw values expanding to the same URL */
    @CXFClient(value = "conflictExpanded", url = HELLO_URL)
    Instance<HelloService> conflictExpandedLiteral;

    @CXFClient(value = "conflictExpanded", url = "${test.hello.url}")
    Instance<HelloService> conflictExpandedExpression;

    /* OQ-3: The attributes are not consulted at all, if client-endpoint-url is set */
    @CXFClient(value = "conflictOverridden", url = HELLO_URL)
    HelloService conflictOverriddenHello;

    @CXFClient(value = "conflictOverridden", url = "${test.not.defined}")
    HelloService conflictOverriddenUndefined;

    /* AC-11: Property expressions */
    @CXFClient(value = "expression", url = "${test.hello.url}")
    HelloService expression;

    @CXFClient(value = "expressionDefault", url = "${test.not.defined:http://localhost:8081/services/hello}")
    HelloService expressionDefault;

    @CXFClient(value = "expressionNestedDefault", url = "${test.not.defined:${test.services.url}}/hello")
    HelloService expressionNestedDefault;

    @CXFClient(value = "expressionLiteral", url = "${test.services.url}/hello")
    HelloService expressionLiteral;

    @CXFClient(value = "expressionProfile", url = "${test.profile.url}")
    HelloService expressionProfile;

    /* AC-12: An expression that cannot be expanded does not matter, if client-endpoint-url is set */
    @CXFClient(value = "undefinedOverridden", url = "${test.not.defined}/hello")
    HelloService undefinedOverridden;

    /* AC-13: An expression that cannot be expanded */
    @CXFClient(value = "undefined", url = "${test.not.defined}/hello")
    Instance<HelloService> undefined;

    @CXFClient(value = "undefined", url = "${test.not.defined}/hello")
    Instance<CXFClientInfo> undefinedInfo;

    /* OQ-5: An expression expanding to an empty string */
    @CXFClient(value = "empty", url = "${test.not.defined:}")
    Instance<HelloService> empty;

    /* OQ-6: Secret handler expressions are not supported */
    @CXFClient(value = "secret", url = "${aes-gcm-nopadding::DJNrZ6LfpupFv6QbXyXhvzD8eVDnDa_kTliQBpuzTobDZxlg}")
    Instance<HelloService> secret;

    /* AC-5: No client name and no configuration having a matching service-interface */
    @CXFClient(url = HELLO_URL)
    UnnamedNoConfigService unnamedNoConfig;

    /* AC-6: No client name and a configuration having a matching service-interface */
    @CXFClient(url = HELLO_URL)
    UnnamedConfigService unnamedConfig;

    @CXFClient(url = HELLO_URL)
    UnnamedConfigUrlService unnamedConfigUrl;

    /* OQ-4: Injection points without a client name are grouped by the service endpoint interface */
    @CXFClient(url = HELLO_URL)
    Instance<UnnamedConflictService> unnamedConflictHello;

    @CXFClient(url = HI_URL)
    Instance<UnnamedConflictService> unnamedConflictHi;

    @Inject
    ConstructorInjectedBean constructorInjectedBean;

    @Test
    void literal() {
        assertThat(literal.hello("Joe")).isEqualTo("Hello Joe");
        assertThat(literalInfo.getEndpointAddress()).isEqualTo(HELLO_URL);
        assertThat(literalInfo.getConfigKey()).isEqualTo("literal");

        final HelloService client = literalInstance.get();
        try {
            assertThat(client.hello("Joe")).isEqualTo("Hello Joe");
        } finally {
            literalInstance.destroy(client);
        }
    }

    @Test
    void constructorParameter() {
        assertThat(constructorInjectedBean.hello("Joe")).isEqualTo("Hello Joe");
    }

    @Test
    void clientEndpointUrlTakesPrecedence() {
        assertThat(both.hello("Joe")).isEqualTo("Hi Joe");
        assertThat(bothInfo.getEndpointAddress()).isEqualTo(HI_URL);
    }

    @Test
    void neither() {
        assertThatThrownBy(() -> create(neither))
                .hasStackTraceContaining("Cannot create the client neither for the service interface " + SEI
                        + ", because quarkus.cxf.client.neither.client-endpoint-url is not set"
                        + " and the url attribute of @CXFClient is not specified either;"
                        + " set quarkus.cxf.client.neither.client-endpoint-url or the url attribute of @CXFClient"
                        + " to the URL of the remote service endpoint");
    }

    @Test
    void conflict() {
        final String message = "Cannot create the client conflict for the service interface " + SEI
                + ", because quarkus.cxf.client.conflict.client-endpoint-url is not set"
                + " and the injection points of that client specify different values of the url attribute of @CXFClient: \""
                + HELLO_URL + "\", \"" + HI_URL + "\";"
                + " either set quarkus.cxf.client.conflict.client-endpoint-url"
                + " or specify the same value of the url attribute of @CXFClient"
                + " on all injection points of the client conflict";
        assertThatThrownBy(() -> create(conflictHello)).hasStackTraceContaining(message);
        assertThatThrownBy(() -> create(conflictHi)).hasStackTraceContaining(message);
        assertThatThrownBy(() -> conflictInfo.get()).hasStackTraceContaining(message);
    }

    @Test
    void conflictEmpty() {
        final String message = "Cannot create the client conflictEmpty for the service interface " + SEI
                + ", because quarkus.cxf.client.conflictEmpty.client-endpoint-url is not set"
                + " and the injection points of that client specify different values of the url attribute of @CXFClient:"
                + " \"\", \"" + HELLO_URL + "\";";
        assertThatThrownBy(() -> create(conflictEmptyHello)).hasStackTraceContaining(message);
        assertThatThrownBy(() -> create(conflictEmpty)).hasStackTraceContaining(message);
    }

    @Test
    void conflictExpanded() {
        /* The raw values are compared and reported: ${test.hello.url} expands to HELLO_URL */
        final String message = "Cannot create the client conflictExpanded for the service interface " + SEI
                + ", because quarkus.cxf.client.conflictExpanded.client-endpoint-url is not set"
                + " and the injection points of that client specify different values of the url attribute of @CXFClient:"
                + " \"${test.hello.url}\", \"" + HELLO_URL + "\";";
        assertThatThrownBy(() -> create(conflictExpandedLiteral)).hasStackTraceContaining(message);
        assertThatThrownBy(() -> create(conflictExpandedExpression)).hasStackTraceContaining(message);
    }

    @Test
    void conflictOverridden() {
        assertThat(conflictOverriddenHello.hello("Joe")).isEqualTo("Hi Joe");
        assertThat(conflictOverriddenUndefined.hello("Joe")).isEqualTo("Hi Joe");
    }

    @Test
    void expressions() {
        assertThat(expression.hello("Joe")).isEqualTo("Hello Joe");
        assertThat(expressionDefault.hello("Joe")).isEqualTo("Hello Joe");
        assertThat(expressionNestedDefault.hello("Joe")).isEqualTo("Hello Joe");
        assertThat(expressionLiteral.hello("Joe")).isEqualTo("Hello Joe");
        assertThat(expressionProfile.hello("Joe")).isEqualTo("Hello Joe");
    }

    @Test
    void undefinedOverridden() {
        assertThat(undefinedOverridden.hello("Joe")).isEqualTo("Hi Joe");
    }

    @Test
    void undefined() {
        final String message = "Cannot create the client undefined for the service interface " + SEI
                + ", because quarkus.cxf.client.undefined.client-endpoint-url is not set"
                + " and @CXFClient(url = \"${test.not.defined}/hello\") cannot be expanded:"
                + " test.not.defined is not available in the application configuration"
                + " and the expression ${test.not.defined} has no default value";
        assertThatThrownBy(() -> create(undefined)).hasStackTraceContaining(message);
        assertThatThrownBy(() -> undefinedInfo.get()).hasStackTraceContaining(message);
    }

    @Test
    void empty() {
        assertThatThrownBy(() -> create(empty))
                .hasStackTraceContaining("Cannot create the client empty for the service interface " + SEI
                        + ", because quarkus.cxf.client.empty.client-endpoint-url is not set"
                        + " and @CXFClient(url = \"${test.not.defined:}\") expands to an empty string;"
                        + " set quarkus.cxf.client.empty.client-endpoint-url or the url attribute of @CXFClient"
                        + " to the URL of the remote service endpoint");
    }

    @Test
    void secret() {
        assertThatThrownBy(() -> create(secret))
                .hasStackTraceContaining("Cannot create the client secret for the service interface " + SEI
                        + ", because quarkus.cxf.client.secret.client-endpoint-url is not set and @CXFClient(url ="
                        + " \"${aes-gcm-nopadding::DJNrZ6LfpupFv6QbXyXhvzD8eVDnDa_kTliQBpuzTobDZxlg}\")"
                        + " cannot be expanded: secret handler expressions, such as"
                        + " ${aes-gcm-nopadding::DJNrZ6LfpupFv6QbXyXhvzD8eVDnDa_kTliQBpuzTobDZxlg}, are not supported");
    }

    @Test
    void unnamedNoConfig() {
        assertThat(unnamedNoConfig.hello("Joe")).isEqualTo("Hello Joe");
        /* The fully qualified class name of the service endpoint interface is the client name */
        final CXFClientInfo info = clientInfo(unnamedNoConfig);
        assertThat(info.getConfigKey()).isEqualTo(UnnamedNoConfigService.class.getName());
        assertThat(info.getEndpointAddress()).isEqualTo(HELLO_URL);
    }

    @Test
    void unnamedConfig() {
        assertThat(unnamedConfig.hello("Joe")).isEqualTo("Hello Joe");
        /* The settings of the configuration having the matching service-interface are used */
        final CXFClientInfo info = clientInfo(unnamedConfig);
        assertThat(info.getConfigKey()).isEqualTo("unnamedConfig");
        assertThat(info.getReceiveTimeout()).isEqualTo(12345L);
        assertThat(info.getEndpointAddress()).isEqualTo(HELLO_URL);
    }

    @Test
    void unnamedConfigUrl() {
        assertThat(unnamedConfigUrl.hello("Joe")).isEqualTo("Hi Joe");
        final CXFClientInfo info = clientInfo(unnamedConfigUrl);
        assertThat(info.getConfigKey()).isEqualTo("unnamedConfigUrl");
        assertThat(info.getEndpointAddress()).isEqualTo(HI_URL);
    }

    @Test
    void unnamedConflict() {
        final String sei = UnnamedConflictService.class.getName();
        final String message = "Cannot create the client " + sei + " for the service interface " + sei
                + ", because quarkus.cxf.client.\"" + sei + "\".client-endpoint-url is not set"
                + " and the injection points of that client specify different values of the url attribute of @CXFClient: \""
                + HELLO_URL + "\", \"" + HI_URL + "\";";
        assertThatThrownBy(() -> create(unnamedConflictHello)).hasStackTraceContaining(message);
        assertThatThrownBy(() -> create(unnamedConflictHi)).hasStackTraceContaining(message);
    }

    private static <T> void create(Instance<T> instance) {
        final T client = instance.get();
        instance.destroy(client);
    }

    private static CXFClientInfo clientInfo(Object client) {
        return (CXFClientInfo) ClientProxy.getClient(client).getEndpoint().get(CXFClientInfo.class.getName());
    }

    @WebService(targetNamespace = NAMESPACE)
    public interface HelloService {
        @WebMethod
        String hello(String person);
    }

    @WebService(targetNamespace = NAMESPACE)
    public interface UnnamedNoConfigService {
        @WebMethod
        String hello(String person);
    }

    @WebService(targetNamespace = NAMESPACE)
    public interface UnnamedConfigService {
        @WebMethod
        String hello(String person);
    }

    @WebService(targetNamespace = NAMESPACE)
    public interface UnnamedConfigUrlService {
        @WebMethod
        String hello(String person);
    }

    @WebService(targetNamespace = NAMESPACE)
    public interface UnnamedConflictService {
        @WebMethod
        String hello(String person);
    }

    @WebService(endpointInterface = "io.quarkiverse.cxf.deployment.test.ClientUrlAnnotationTest$HelloService", serviceName = "HelloService")
    public static class HelloServiceImpl implements HelloService {
        @Override
        public String hello(String person) {
            return "Hello " + person;
        }
    }

    @WebService(endpointInterface = "io.quarkiverse.cxf.deployment.test.ClientUrlAnnotationTest$HelloService", serviceName = "HelloService")
    public static class HiServiceImpl implements HelloService {
        @Override
        public String hello(String person) {
            return "Hi " + person;
        }
    }

    @ApplicationScoped
    public static class ConstructorInjectedBean {
        private final HelloService client;

        ConstructorInjectedBean(@CXFClient(value = "constructor", url = HELLO_URL) HelloService client) {
            this.client = client;
        }

        public String hello(String person) {
            return client.hello(person);
        }
    }
}
