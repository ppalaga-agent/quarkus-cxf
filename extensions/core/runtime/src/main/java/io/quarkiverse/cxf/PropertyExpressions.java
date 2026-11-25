package io.quarkiverse.cxf;

import static io.smallrye.common.expression.Expression.Flag.DOUBLE_COLON;
import static io.smallrye.common.expression.Expression.Flag.ESCAPES;
import static io.smallrye.common.expression.Expression.Flag.LENIENT_SYNTAX;
import static io.smallrye.common.expression.Expression.Flag.NO_$$;
import static io.smallrye.common.expression.Expression.Flag.NO_SMART_BRACES;
import static io.smallrye.common.expression.Expression.Flag.NO_TRIM;

import org.eclipse.microprofile.config.ConfigProvider;

import io.smallrye.common.expression.Expression;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.SmallRyeConfig;

/**
 * An expander of property expressions, such as <code>${key}</code> or <code>${key:default}</code>, in strings that do
 * not come from the application configuration, such as annotation attribute values.
 * <p>
 * SmallRye Config has no public API for expanding the expressions in an arbitrary string.
 * This class is the only place where Quarkus CXF works that around, so that it can be replaced once
 * <a href="https://github.com/quarkiverse/quarkus-cxf/issues/2473">#2473</a> is resolved.
 * <p>
 * The syntax is the one accepted by {@code io.smallrye.config.ExpressionConfigSourceInterceptor}, and thus the one of
 * {@code application.properties}, except that secret handler expressions, such as <code>${handler::value}</code>, are
 * not supported.
 *
 * @since 4.1.0
 */
final class PropertyExpressions {

    private PropertyExpressions() {
    }

    /**
     * Expands the property expressions in the given {@code value} using the configuration of the current application.
     *
     * @param value the string to expand
     * @return the given {@code value} with all property expressions expanded
     * @throws IllegalArgumentException if any of the expressions cannot be expanded; the message names the key that
     *         could not be resolved and it does not contain any values coming from the configuration
     * @since 4.1.0
     */
    static String expand(String value) {
        return expand(value, ConfigProvider.getConfig().unwrap(SmallRyeConfig.class));
    }

    /**
     * Expands the property expressions in the given {@code value}.
     * The keys are looked up in the given {@code config}, so that the values may contain further expressions and they
     * may be profile specific.
     * An expression having a default value, such as <code>${key:default}</code>, expands to that default value, if the
     * key is not available in the given {@code config}.
     *
     * @param value the string to expand
     * @param config where to look up the keys
     * @return the given {@code value} with all property expressions expanded
     * @throws IllegalArgumentException if any of the expressions cannot be expanded, because its key is not available in
     *         the given {@code config} and it has no default value, or because the value of the key cannot be expanded,
     *         or because it is a secret handler expression; the message names the key that could not be resolved and it
     *         does not contain any values coming from the given {@code config}
     * @since 4.1.0
     */
    static String expand(String value, SmallRyeConfig config) {
        if (!value.contains("${")) {
            /* Same as in ExpressionConfigSourceInterceptor: no escape sequences are handled in such values */
            return value;
        }
        /* Keep the flags in sync with ExpressionConfigSourceInterceptor */
        return Expression.compile(value, ESCAPES, LENIENT_SYNTAX, DOUBLE_COLON, NO_TRIM, NO_SMART_BRACES, NO_$$)
                .evaluate((resolveContext, result) -> {
                    final String key = resolveContext.getKey();
                    if (key.contains("::")) {
                        throw new IllegalArgumentException("secret handler expressions, such as ${" + key
                                + "}, are not supported");
                    }
                    final ConfigValue configValue;
                    try {
                        configValue = config.getConfigValue(key);
                    } catch (RuntimeException e) {
                        throw new IllegalArgumentException(
                                "the value of " + key + " cannot be read from the application configuration: "
                                        + e.getMessage(),
                                e);
                    }
                    if (configValue.hasProblems()) {
                        throw new IllegalArgumentException(
                                "the value of " + key + " cannot be read from the application configuration: "
                                        + configValue.getProblems().get(0).getMessage());
                    }
                    if (configValue.getValue() != null) {
                        result.append(configValue.getValue());
                    } else if (resolveContext.hasDefault()) {
                        resolveContext.expandDefault();
                    } else {
                        throw new IllegalArgumentException(
                                key + " is not available in the application configuration"
                                        + " and the expression ${" + key + "} has no default value");
                    }
                });
    }

}
