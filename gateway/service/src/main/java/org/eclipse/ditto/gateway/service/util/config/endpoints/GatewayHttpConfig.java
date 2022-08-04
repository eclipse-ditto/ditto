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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.service.config.http.DefaultHttpConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements the HTTP config that is specific for the Ditto Gateway service.
 */
@Immutable
public final class GatewayHttpConfig implements HttpConfig {

    private final String hostname;
    private final int port;
    private final Set<JsonSchemaVersion> schemaVersions;
    private final List<String> protocolHeaders;
    private final boolean forceHttps;
    private final boolean redirectToHttps;
    private final Pattern redirectToHttpsBlocklistPattern;
    private final boolean enableCors;
    private final Duration requestTimeout;
    private final Set<HeaderDefinition> queryParamsAsHeaders;
    private final Set<String> additionalAcceptedMediaTypes;
    private final Duration coordinatedShutdownTimeout;

    private GatewayHttpConfig(final DefaultHttpConfig basicHttpConfig, final ScopedConfig scopedConfig) {
        hostname = basicHttpConfig.getHostname();
        port = basicHttpConfig.getPort();
        coordinatedShutdownTimeout = basicHttpConfig.getCoordinatedShutdownTimeout();
        schemaVersions = Collections.unmodifiableSet(getJsonSchemaVersions(scopedConfig));
        protocolHeaders = readProtocolHeaders(scopedConfig);
        forceHttps = scopedConfig.getBoolean(GatewayHttpConfigValue.FORCE_HTTPS.getConfigPath());
        redirectToHttps = scopedConfig.getBoolean(GatewayHttpConfigValue.REDIRECT_TO_HTTPS.getConfigPath());
        redirectToHttpsBlocklistPattern = tryToCreateBlocklistPattern(scopedConfig);
        enableCors = scopedConfig.getBoolean(GatewayHttpConfigValue.ENABLE_CORS.getConfigPath());
        requestTimeout = scopedConfig.getDuration(GatewayHttpConfigValue.REQUEST_TIMEOUT.getConfigPath());
        queryParamsAsHeaders = Collections.unmodifiableSet(getQueryParameterNamesAsHeaderDefinitions(scopedConfig));
        additionalAcceptedMediaTypes =
                Set.of(scopedConfig.getString(GatewayHttpConfigValue.ADDITIONAL_ACCEPTED_MEDIA_TYPES.getConfigPath())
                        .split(","));
    }

    private static List<String> readProtocolHeaders(final ScopedConfig scopedConfig) {
        return scopedConfig.getStringList(GatewayHttpConfigValue.PROTOCOL_HEADERS.getConfigPath());
    }

    private static Set<JsonSchemaVersion> getJsonSchemaVersions(final Config httpScopedConfig) {
        final List<Integer> schemaVersionNumbers =
                httpScopedConfig.getIntList(GatewayHttpConfigValue.SCHEMA_VERSIONS.getConfigPath());

        final Set<JsonSchemaVersion> result = EnumSet.noneOf(JsonSchemaVersion.class);
        schemaVersionNumbers.forEach(schemaVersionNumber -> {
            final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.forInt(schemaVersionNumber)
                    .orElseThrow(() -> {
                        final String msgPattern = "Schema version <{0}> is not supported!";
                        return new DittoConfigError(MessageFormat.format(msgPattern, schemaVersionNumber));
                    });
            result.add(jsonSchemaVersion);
        });
        return result;
    }

    private static Pattern tryToCreateBlocklistPattern(final Config httpScopedConfig) {
        try {
            return createBlocklistPattern(httpScopedConfig);
        } catch (final PatternSyntaxException e) {
            throw new DittoConfigError(MessageFormat.format("Failed to get <{0}> as Pattern!",
                    GatewayHttpConfigValue.REDIRECT_TO_HTTPS_BLOCKLIST_PATTERN.getConfigPath()), e);
        }
    }

    private static Pattern createBlocklistPattern(final Config httpScopedConfig) {
        return Pattern.compile(
                httpScopedConfig.getString(GatewayHttpConfigValue.REDIRECT_TO_HTTPS_BLOCKLIST_PATTERN.getConfigPath()));
    }

    private static Set<HeaderDefinition> getQueryParameterNamesAsHeaderDefinitions(final Config scopedConfig) {
        final List<String> queryParamNames =
                scopedConfig.getStringList(GatewayHttpConfigValue.QUERY_PARAMS_AS_HEADERS.getConfigPath());

        final Set<HeaderDefinition> result = new LinkedHashSet<>(queryParamNames.size() << 1);
        final Set<String> unknownHeaderKeys = new LinkedHashSet<>(3);
        for (final String queryParamName : queryParamNames) {
            final Optional<HeaderDefinition> headerDefinitionOptional = DittoHeaderDefinition.forKey(queryParamName);
            if (headerDefinitionOptional.isPresent()) {
                result.add(headerDefinitionOptional.get());
            } else {
                unknownHeaderKeys.add(queryParamName);
            }
        }
        throwConfigErrorIfHeaderKeysUnknown(unknownHeaderKeys);
        return result;
    }

    private static void throwConfigErrorIfHeaderKeysUnknown(final Set<String> unknownHeaderKeys) {
        final int unknownHeaderKeysSize = unknownHeaderKeys.size();
        if (0 < unknownHeaderKeysSize) {
            final String msgPattern;
            if (1 == unknownHeaderKeysSize) {
                msgPattern = "The query parameter name <{0}> does not denote a known header key!";
            } else {
                msgPattern = "The query parameter names <{0}> do not denote known header keys!";
            }
            throw new DittoConfigError(MessageFormat.format(msgPattern, unknownHeaderKeys));
        }
    }

    /**
     * Returns an instance of {@code GatewayHttpConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the HTTP settings of the Gateway service.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static GatewayHttpConfig of(final Config config) {
        final DefaultHttpConfig basicHttpConfig = DefaultHttpConfig.of(config);

        return new GatewayHttpConfig(basicHttpConfig,
                ConfigWithFallback.newInstance(config, basicHttpConfig.getConfigPath(),
                        GatewayHttpConfigValue.values()));
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
    public Duration getCoordinatedShutdownTimeout() {
        return coordinatedShutdownTimeout;
    }

    @Override
    public Set<JsonSchemaVersion> getSupportedSchemaVersions() {
        return schemaVersions;
    }

    @Override
    public List<String> getProtocolHeaders() {
        return protocolHeaders;
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
    public Pattern getRedirectToHttpsBlocklistPattern() {
        return redirectToHttpsBlocklistPattern;
    }

    @Override
    public boolean isEnableCors() {
        return enableCors;
    }

    @Override
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    @Override
    public Set<HeaderDefinition> getQueryParametersAsHeaders() {
        return queryParamsAsHeaders;
    }

    @Override
    public Set<String> getAdditionalAcceptedMediaTypes() {
        return additionalAcceptedMediaTypes;
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
                Objects.equals(coordinatedShutdownTimeout, that.coordinatedShutdownTimeout) &&
                Objects.equals(protocolHeaders, that.protocolHeaders) &&
                forceHttps == that.forceHttps &&
                redirectToHttps == that.redirectToHttps &&
                enableCors == that.enableCors &&
                hostname.equals(that.hostname) &&
                schemaVersions.equals(that.schemaVersions) &&
                redirectToHttpsBlocklistPattern.equals(that.redirectToHttpsBlocklistPattern) &&
                requestTimeout.equals(that.requestTimeout) &&
                queryParamsAsHeaders.equals(that.queryParamsAsHeaders) &&
                additionalAcceptedMediaTypes.equals(that.additionalAcceptedMediaTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, port, coordinatedShutdownTimeout, schemaVersions, protocolHeaders, forceHttps,
                redirectToHttps, redirectToHttpsBlocklistPattern, enableCors, requestTimeout,
                queryParamsAsHeaders, additionalAcceptedMediaTypes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "hostname=" + hostname +
                ", port=" + port +
                ", coordinatedShutdownTimeout=" + coordinatedShutdownTimeout +
                ", schemaVersions=" + schemaVersions +
                ", protocolHeaders=" + protocolHeaders +
                ", forceHttps=" + forceHttps +
                ", redirectToHttps=" + redirectToHttps +
                ", redirectToHttpsBlocklistPattern=" + redirectToHttpsBlocklistPattern +
                ", enableCors=" + enableCors +
                ", requestTimeout=" + requestTimeout +
                ", queryParamsAsHeaders=" + queryParamsAsHeaders +
                ", additionalAcceptedMediaTypes=" + additionalAcceptedMediaTypes +
                "]";
    }

}
