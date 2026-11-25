package io.quarkiverse.cxf.deployment.test.dev;

import jakarta.enterprise.inject.Instance;
import jakarta.jws.WebService;

import io.quarkiverse.cxf.annotation.CXFClient;

@WebService(name = "DevUiStats", serviceName = "DevUiStats")
public class DevUiRemoteStatsImpl implements DevUiStats {

    @CXFClient("stats")
    DevUiStats stats;

    @CXFClient
    DevUiStats keyLessStats;

    /** A client without {@code client-endpoint-url} that cannot be created, but still has to be listed in Dev UI */
    @CXFClient("noUrl")
    Instance<DevUiStats> noUrlStats;

    /** A client whose URL is specified only through {@link CXFClient#url()} */
    @CXFClient(value = "annotationUrl", url = "${test.stats.url}")
    DevUiStats annotationUrlStats;

    /** {@code client-endpoint-url} takes precedence over {@link CXFClient#url()} */
    @CXFClient(value = "bothUrls", url = "http://localhost:8080/services/remote-stats")
    DevUiStats bothUrlsStats;

    /** The injection points specify different URLs, so the client cannot be created, but it still has to be listed */
    @CXFClient(value = "conflictingUrls", url = "http://localhost:8080/services/stats")
    Instance<DevUiStats> conflictingUrlsStats;

    @CXFClient(value = "conflictingUrls", url = "http://localhost:8080/services/remote-stats")
    Instance<DevUiStats> conflictingUrlsRemoteStats;

    /** The property expression cannot be expanded, so the client cannot be created, but it still has to be listed */
    @CXFClient(value = "undefinedUrl", url = "${test.not.defined}")
    Instance<DevUiStats> undefinedUrlStats;

    @Override
    public int getClientCount() {
        return stats.getClientCount();
    }

    @Override
    public String getClient(int index) {
        return stats.getClient(index);
    }

    @Override
    public int getServiceCount() {
        return stats.getServiceCount();
    }

    @Override
    public String getService(int index) {
        return stats.getService(index);
    }

}
