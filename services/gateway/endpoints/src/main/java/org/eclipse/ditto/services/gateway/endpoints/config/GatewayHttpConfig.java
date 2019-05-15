/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.http.DefaultHttpConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements the HTTP config that is specific for the Ditto Gateway service.
 */
@Immutable
public final class GatewayHttpConfig implements HttpConfig, Serializable {

    private static final long serialVersionUID = 2115204753471363660L;

    private final String hostname;
    private final int port;
    private final Set<Integer> schemaVersions;
    private final boolean forceHttps;
    private final boolean redirectToHttps;
    private final Pattern redirectToHttpsBlacklistPattern;
    private final boolean enableCors;
    private final Duration requestTimeout;

    private GatewayHttpConfig(final DefaultHttpConfig basicHttpConfig, final ScopedConfig scopedConfig,
            final Pattern redirectToHttpsBlacklistPattern) {

        hostname = basicHttpConfig.getHostname();
        port = basicHttpConfig.getPort();
        schemaVersions = Collections.unmodifiableSet(
                new HashSet<>(scopedConfig.getIntList(GatewayHttpConfigValue.SCHEMA_VERSIONS.getConfigPath())));
        forceHttps = scopedConfig.getBoolean(GatewayHttpConfigValue.FORCE_HTTPS.getConfigPath());
        redirectToHttps = scopedConfig.getBoolean(GatewayHttpConfigValue.REDIRECT_TO_HTTPS.getConfigPath());
        this.redirectToHttpsBlacklistPattern = redirectToHttpsBlacklistPattern;
        enableCors = scopedConfig.getBoolean(GatewayHttpConfigValue.ENABLE_CORS.getConfigPath());
        requestTimeout = scopedConfig.getDuration(GatewayHttpConfigValue.REQUEST_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code GatewayHttpConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the HTTP settings of the Gateway service.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static GatewayHttpConfig of(final Config config) {
        final DefaultHttpConfig basicHttpConfig = DefaultHttpConfig.of(config);
        final ConfigWithFallback httpScopedConfig =
                ConfigWithFallback.newInstance(config, basicHttpConfig.getConfigPath(),
                        GatewayHttpConfigValue.values());

        return new GatewayHttpConfig(basicHttpConfig, httpScopedConfig, tryToCreateBlacklistPattern(httpScopedConfig));
    }

    private static Pattern tryToCreateBlacklistPattern(final ScopedConfig httpScopedConfig) {
        try {
            return createBlacklistPattern(httpScopedConfig);
        } catch (final PatternSyntaxException e) {
            throw new DittoConfigError(MessageFormat.format("Failed to get <{0}> as Pattern!",
                    GatewayHttpConfigValue.REDIRECT_TO_HTTPS_BLACKLIST_PATTERN.getConfigPath()), e);
        }
    }

    private static Pattern createBlacklistPattern(final Config httpScopedConfig) {
        return Pattern.compile(
                httpScopedConfig.getString(GatewayHttpConfigValue.REDIRECT_TO_HTTPS_BLACKLIST_PATTERN.getConfigPath()));
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public Set<Integer> getSupportedSchemaVersions() {
        return schemaVersions;
    }

    @Override
    public boolean isForceHttps() {
        return forceHttps;
    }

    @Override
    public boolean isRedirectToHttps() {
        return redirectToHttps;
    }

    @Override
    public Pattern getRedirectToHttpsBlacklistPattern() {
        return redirectToHttpsBlacklistPattern;
    }

    @Override
    public boolean isEnableCors() {
        return enableCors;
    }

    @Override
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GatewayHttpConfig that = (GatewayHttpConfig) o;
        return port == that.port &&
                forceHttps == that.forceHttps &&
                redirectToHttps == that.redirectToHttps &&
                enableCors == that.enableCors &&
                hostname.equals(that.hostname) &&
                schemaVersions.equals(that.schemaVersions) &&
                redirectToHttpsBlacklistPattern.equals(that.redirectToHttpsBlacklistPattern) &&
                requestTimeout.equals(that.requestTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, port, schemaVersions, forceHttps, redirectToHttps,
                redirectToHttpsBlacklistPattern,
                enableCors, requestTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "hostname=" + hostname +
                ", port=" + port +
                ", schemaVersions=" + schemaVersions +
                ", forceHttps=" + forceHttps +
                ", redirectToHttps=" + redirectToHttps +
                ", redirectToHttpsBlacklistPattern=" + redirectToHttpsBlacklistPattern +
                ", enableCors=" + enableCors +
                ", requestTimeout=" + requestTimeout +
                "]";
    }

}
