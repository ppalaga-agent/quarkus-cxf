package io.quarkiverse.cxf;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.quarkiverse.cxf.annotation.CXFClient;

/**
 * The values of {@link CXFClient#url()} of all {@link CXFClient} injection points found at build time, grouped by
 * client name, along with the algorithm for figuring out the URL of the remote service endpoint of a client.
 * <p>
 * The instances are immutable.
 *
 * @since 4.1.0
 */
public final class CXFClientUrls {

    private final Map<String, Set<String>> urlsByClientName;

    /**
     * @param urlsByClientName the raw values of {@link CXFClient#url()}, without their property expressions expanded,
     *        of all injection points having the client name given by the key of the map entry;
     *        an injection point not specifying {@link CXFClient#url()} contributes an empty string;
     *        an injection point without a client name is listed under the name of the only
     *        {@code quarkus.cxf.client."client-name"} having {@code service-interface} set to its Service Endpoint
     *        Interface, or under the fully qualified class name of its Service Endpoint Interface if there is no such
     *        configuration
     * @since 4.1.0
     */
    public CXFClientUrls(Map<String, ? extends Collection<String>> urlsByClientName) {
        final Map<String, Set<String>> copy = new TreeMap<>();
        urlsByClientName.forEach((clientName, urls) -> copy.put(clientName, Collections.unmodifiableSet(new TreeSet<>(urls))));
        this.urlsByClientName = Collections.unmodifiableMap(copy);
    }

    /**
     * @param clientName the client name, such as {@code client-name} in
     *        {@code quarkus.cxf.client."client-name".client-endpoint-url}
     * @return an immutable sorted set of distinct raw values of {@link CXFClient#url()} of all injection points of the
     *         given client found at build time; an empty set if no injection point of the given client was found
     * @since 4.1.0
     */
    public Set<String> getUrls(String clientName) {
        return urlsByClientName.getOrDefault(clientName, Collections.emptySet());
    }

    /**
     * @param clientName the client name, such as {@code client-name} in
     *        {@code quarkus.cxf.client."client-name".client-endpoint-url}
     * @param injectionPointUrl the raw value of {@link CXFClient#url()} of the injection point that is currently being
     *        satisfied
     * @return an immutable sorted set of distinct raw values consisting of {@link #getUrls(String)} of the given client
     *         and the given {@code injectionPointUrl}
     * @since 4.1.0
     */
    public Set<String> getUrls(String clientName, String injectionPointUrl) {
        Objects.requireNonNull(injectionPointUrl, "injectionPointUrl");
        final Set<String> urls = getUrls(clientName);
        if (urls.contains(injectionPointUrl)) {
            return urls;
        }
        final Set<String> result = new TreeSet<>(urls);
        result.add(injectionPointUrl);
        return Collections.unmodifiableSet(result);
    }

    /**
     * Figures out the URL of the remote service endpoint the given client sends its requests to:
     * <ol>
     * <li>If {@code quarkus.cxf.client."client-name".client-endpoint-url} is set in the given {@code config}, it is
     * returned and the given {@code annotationUrls} are not evaluated at all.
     * <li>Otherwise, if the given {@code annotationUrls} consist of a single non-empty value, that value is returned
     * with its property expressions expanded.
     * </ol>
     *
     * @param config the configuration of the given client
     * @param clientName the name of the given client, such as {@code client-name} in
     *        {@code quarkus.cxf.client."client-name".client-endpoint-url}
     * @param sei the fully qualified class name of the Service Endpoint Interface of the given client
     * @param annotationUrls the distinct raw values of {@link CXFClient#url()} of all injection points of the given
     *        client, as returned by {@link #getUrls(String, String)}
     * @return the URL of the remote service endpoint; never {@code null} or blank
     * @throws IllegalStateException if {@code client-endpoint-url} is not set and
     *         <ul>
     *         <li>no injection point specifies {@link CXFClient#url()}, or
     *         <li>the injection points specify different values of {@link CXFClient#url()}, or
     *         <li>the property expressions in the value of {@link CXFClient#url()} cannot be expanded, or
     *         <li>the value of {@link CXFClient#url()} expands to an empty or blank string.
     *         </ul>
     *         The message contains only the raw unexpanded values of {@link CXFClient#url()}.
     * @since 4.1.0
     */
    public static String requireEndpointUrl(
            CxfClientConfig config,
            String clientName,
            String sei,
            Set<String> annotationUrls) {
        final Optional<String> configuredUrl = config.clientEndpointUrl();
        if (configuredUrl.isPresent()) {
            return configuredUrl.get();
        }
        final String option = "quarkus.cxf.client." + QuarkusCxfUtils.quoteCongurationKeyIfNeeded(clientName)
                + ".client-endpoint-url";
        final String attribute = "the url attribute of @" + CXFClient.class.getSimpleName();
        final String messagePrefix = "Cannot create the client " + clientName + " for the service interface " + sei
                + ", because " + option + " is not set and ";
        if (annotationUrls.size() > 1) {
            throw new IllegalStateException(messagePrefix
                    + "the injection points of that client specify different values of " + attribute + ": "
                    + annotationUrls.stream().collect(Collectors.joining("\", \"", "\"", "\""))
                    + "; either set " + option + " or specify the same value of " + attribute
                    + " on all injection points of the client " + clientName);
        }
        final String rawUrl = annotationUrls.isEmpty() ? "" : annotationUrls.iterator().next();
        if (rawUrl.isEmpty()) {
            throw new IllegalStateException(messagePrefix + attribute + " is not specified either; set " + option
                    + " or " + attribute + " to the URL of the remote service endpoint");
        }
        final String rawAttribute = "@" + CXFClient.class.getSimpleName() + "(url = \"" + rawUrl + "\")";
        final String url;
        try {
            url = PropertyExpressions.expand(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(messagePrefix + rawAttribute + " cannot be expanded: " + e.getMessage(), e);
        }
        if (url.isBlank()) {
            throw new IllegalStateException(messagePrefix + rawAttribute + " expands to an empty string; set " + option
                    + " or " + attribute + " to the URL of the remote service endpoint");
        }
        return url;
    }

}
