/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.http.DefaultHttpProxyConfig;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link HttpPushConfig}.
 */
@Immutable
final class DefaultHttpPushConfig implements HttpPushConfig, WithStringMapDecoding {

    private static final String CONFIG_PATH = "http-push";

    private final int maxQueueSize;
    private final Duration requestTimeout;
    private final HttpProxyConfig httpProxyConfig;
    private final Map<String, String> hmacAlgorithms;
    private final OAuth2Config oAuth2Config;
    private final List<String> omitRequestBodyMethods;

    private DefaultHttpPushConfig(final ScopedConfig config) {
        maxQueueSize = config.getPositiveIntOrThrow(ConfigValue.MAX_QUEUE_SIZE);
        requestTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.REQUEST_TIMEOUT);
        httpProxyConfig = DefaultHttpProxyConfig.ofProxy(config);
        hmacAlgorithms = asStringMap(config, ConfigValue.HMAC_ALGORITHMS.getConfigPath());
        oAuth2Config = DefaultOAuth2Config.of(config);
        omitRequestBodyMethods = config.getStringList(ConfigValue.OMIT_REQUEST_BODY_METHODS.getConfigPath());
    }

    static DefaultHttpPushConfig of(final Config config) {
        return new DefaultHttpPushConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @Override
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    @Override
    public HttpProxyConfig getHttpProxyConfig() {
        return httpProxyConfig;
    }

    @Override
    public Map<String, String> getHmacAlgorithms() {
        return hmacAlgorithms;
    }

    @Override
    public OAuth2Config getOAuth2Config() {
        return oAuth2Config;
    }

    @Override
    public List<String> getOmitRequestBodyMethods() {
        return omitRequestBodyMethods;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpPushConfig that = (DefaultHttpPushConfig) o;
        return maxQueueSize == that.maxQueueSize &&
                Objects.equals(requestTimeout, that.requestTimeout) &&
                Objects.equals(httpProxyConfig, that.httpProxyConfig) &&
                Objects.equals(hmacAlgorithms, that.hmacAlgorithms) &&
                Objects.equals(oAuth2Config, that.oAuth2Config) &&
                Objects.equals(omitRequestBodyMethods, that.omitRequestBodyMethods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxQueueSize, httpProxyConfig, hmacAlgorithms, requestTimeout, oAuth2Config,
                omitRequestBodyMethods);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxQueueSize=" + maxQueueSize +
                ", requestTimeout=" + requestTimeout +
                ", httpProxyConfig=" + httpProxyConfig +
                ", hmacAlgorithms=" + hmacAlgorithms +
                ", oAuth2Config=" + oAuth2Config +
                ", omitRequestBodyMethods=" + omitRequestBodyMethods +
                "]";
    }

}
