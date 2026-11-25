package io.quarkiverse.cxf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class PropertyExpressionsTest {

    private static final SmallRyeConfig CONFIG = new SmallRyeConfigBuilder()
            .addDefaultInterceptors()
            .withProfile("prod")
            .withSources(new PropertiesConfigSource(
                    Map.of(
                            "base.uri", "https://example.org",
                            "service.url", "${base.uri}/service",
                            "broken.url", "${not.defined}/service",
                            "profile.url", "https://dev.example.org",
                            "%prod.profile.url", "https://prod.example.org",
                            "secret", "s3cr3t",
                            "key.name", "base.uri"),
                    "test",
                    100))
            .build();

    @Test
    void literal() {
        assertThat(expand("")).isEqualTo("");
        assertThat(expand("https://example.org/service")).isEqualTo("https://example.org/service");
        /* As in application.properties, values without any expression are not touched */
        assertThat(expand("C:\\dir $HOME {}")).isEqualTo("C:\\dir $HOME {}");
    }

    @Test
    void expression() {
        assertThat(expand("${base.uri}")).isEqualTo("https://example.org");
        assertThat(expand("${base.uri}/service")).isEqualTo("https://example.org/service");
        assertThat(expand("${base.uri}/${key.name}")).isEqualTo("https://example.org/base.uri");
        /* The value of the key contains an expression */
        assertThat(expand("${service.url}?wsdl")).isEqualTo("https://example.org/service?wsdl");
        /* The key contains an expression */
        assertThat(expand("${${key.name}}/service")).isEqualTo("https://example.org/service");
        assertThat(expand("${profile.url}")).isEqualTo("https://prod.example.org");
    }

    @Test
    void defaultValue() {
        assertThat(expand("${base.uri:https://default.example.org}/service")).isEqualTo("https://example.org/service");
        assertThat(expand("${not.defined:https://default.example.org}/service"))
                .isEqualTo("https://default.example.org/service");
        assertThat(expand("${not.defined:${base.uri}}/service")).isEqualTo("https://example.org/service");
        assertThat(expand("${not.defined:}")).isEqualTo("");
    }

    @Test
    void escaped() {
        assertThat(expand("\\${base.uri}/service")).isEqualTo("${base.uri}/service");
    }

    @Test
    void notDefined() {
        assertThatThrownBy(() -> expand("${not.defined}/service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not.defined is not available in the application configuration"
                        + " and the expression ${not.defined} has no default value");
        assertThatThrownBy(() -> expand("${base.uri}/${not.defined:${not.defined.either}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not.defined.either is not available in the application configuration"
                        + " and the expression ${not.defined.either} has no default value");
    }

    @Test
    void notDefinedInValue() {
        assertThatThrownBy(() -> expand("${broken.url}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("the value of broken.url cannot be read from the application configuration: ")
                .hasMessageContaining("not.defined");
    }

    @Test
    void secretHandler() {
        assertThatThrownBy(() -> expand("${handler::secret}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("secret handler expressions, such as ${handler::secret}, are not supported");
    }

    @Test
    void noValuesInMessages() {
        assertThatThrownBy(() -> expand("${secret}/${not.defined}"))
                .isInstanceOf(IllegalArgumentException.class)
                .message().doesNotContain("s3cr3t");
    }

    private static String expand(String value) {
        return PropertyExpressions.expand(value, CONFIG);
    }
}
