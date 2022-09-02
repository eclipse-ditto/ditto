/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.security;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

/**
 * This class is the default implementation of the OAuth config.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class DefaultOAuthConfig implements OAuthConfig {

    private static final String CONFIG_PATH = "oauth";

    private final String protocol;
    private final Duration allowedClockSkew;
    private final Map<SubjectIssuer, SubjectIssuerConfig> openIdConnectIssuers;
    private final Map<SubjectIssuer, SubjectIssuerConfig> openIdConnectIssuersExtension;
    private final String tokenIntegrationSubject;

    private DefaultOAuthConfig(final ConfigWithFallback configWithFallback) {
        protocol = configWithFallback.getString(OAuthConfigValue.PROTOCOL.getConfigPath());
        allowedClockSkew = configWithFallback.getDuration(OAuthConfigValue.ALLOWED_CLOCK_SKEW.getConfigPath());
        openIdConnectIssuers = loadIssuers(configWithFallback, OAuthConfigValue.OPENID_CONNECT_ISSUERS);
        openIdConnectIssuersExtension =
                loadIssuers(configWithFallback, OAuthConfigValue.OPENID_CONNECT_ISSUERS_EXTENSION);
        tokenIntegrationSubject =
                configWithFallback.getString(OAuthConfigValue.TOKEN_INTEGRATION_SUBJECT.getConfigPath());
    }

    private static Map<SubjectIssuer, SubjectIssuerConfig> loadIssuers(final ConfigWithFallback config,
            final KnownConfigValue configValue) {

        final ConfigObject issuersConfig = config.getObject(configValue.getConfigPath());

        return issuersConfig.entrySet().stream().collect(SubjectIssuerCollector.toSubjectIssuerMap());
    }

    /**
     * Returns an instance of {@code DefaultOAuthConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings for OAuth config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultOAuthConfig of(final Config config) {
        return new DefaultOAuthConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, OAuthConfigValue.values()));
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public Duration getAllowedClockSkew() {
        return allowedClockSkew;
    }

    @Override
    public Map<SubjectIssuer, SubjectIssuerConfig> getOpenIdConnectIssuers() {
        return openIdConnectIssuers;
    }

    @Override
    public Map<SubjectIssuer, SubjectIssuerConfig> getOpenIdConnectIssuersExtension() {
        return openIdConnectIssuersExtension;
    }

    @Override
    public String getTokenIntegrationSubject() {
        return tokenIntegrationSubject;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultOAuthConfig that = (DefaultOAuthConfig) o;
        return Objects.equals(protocol, that.protocol)
                && Objects.equals(allowedClockSkew, that.allowedClockSkew)
                && Objects.equals(openIdConnectIssuers, that.openIdConnectIssuers)
                && Objects.equals(openIdConnectIssuersExtension, that.openIdConnectIssuersExtension)
                && Objects.equals(tokenIntegrationSubject, that.tokenIntegrationSubject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, allowedClockSkew, openIdConnectIssuers, openIdConnectIssuersExtension,
                tokenIntegrationSubject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "protocol=" + protocol +
                ", allowedClocSkew=" + allowedClockSkew +
                ", openIdConnectIssuers=" + openIdConnectIssuers +
                ", openIdConnectIssuersExtension=" + openIdConnectIssuersExtension +
                ", tokenIntegrationSubject=" + tokenIntegrationSubject +
                "]";
    }

    private static class SubjectIssuerCollector
            implements
            Collector<Map.Entry<String, ConfigValue>, Map<SubjectIssuer, SubjectIssuerConfig>, Map<SubjectIssuer,
                    SubjectIssuerConfig>> {

        private static SubjectIssuerCollector toSubjectIssuerMap() {
            return new SubjectIssuerCollector();
        }

        @Override
        public Supplier<Map<SubjectIssuer, SubjectIssuerConfig>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<SubjectIssuer, SubjectIssuerConfig>, Map.Entry<String, ConfigValue>> accumulator() {
            return (map, entry) -> map.put(SubjectIssuer.newInstance(entry.getKey()),
                    DefaultSubjectIssuerConfig.of(entry.getKey(), ConfigFactory.empty().withFallback(entry.getValue())));
        }

        @Override
        public BinaryOperator<Map<SubjectIssuer, SubjectIssuerConfig>> combiner() {
            return (left, right) -> Stream.concat(left.entrySet().stream(), right.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @Override
        public Function<Map<SubjectIssuer, SubjectIssuerConfig>, Map<SubjectIssuer, SubjectIssuerConfig>> finisher() {
            return map -> Collections.unmodifiableMap(new HashMap<>(map));
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.singleton(Characteristics.UNORDERED);
        }
    }

}
