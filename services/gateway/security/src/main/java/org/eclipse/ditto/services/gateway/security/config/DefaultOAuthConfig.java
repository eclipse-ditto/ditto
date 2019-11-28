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
package org.eclipse.ditto.services.gateway.security.config;

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

import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

/**
 * This class is the default implementation of the OAuth config.
 */
@Immutable
public final class DefaultOAuthConfig implements OAuthConfig {

    private static final String CONFIG_PATH = "oauth";

    private final Map<SubjectIssuer, String> openIdConnectIssuers;
    private final Map<SubjectIssuer, String> openIdConnectIssuersExtension;

    private DefaultOAuthConfig(final ConfigWithFallback configWithFallback) {
        openIdConnectIssuers = loadIssuers(configWithFallback, OAuthConfigValue.OPENID_CONNECT_ISSUERS);
        openIdConnectIssuersExtension =
                loadIssuers(configWithFallback, OAuthConfigValue.OPENID_CONNECT_ISSUERS_EXTENSION);
    }

    private static Map<SubjectIssuer, String> loadIssuers(final ConfigWithFallback config,
            final KnownConfigValue configValue) {
        final Config issuersConfig = config.getConfig(configValue.getConfigPath());
        return issuersConfig.entrySet().stream().collect(SubjectIssuerCollector.toSubjectIssuerMap());
    }

    /**
     * Returns an instance of {@code DefaultOAuthConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings for OAuth config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultOAuthConfig of(final Config config) {
        return new DefaultOAuthConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, OAuthConfigValue.values()));
    }

    @Override
    public Map<SubjectIssuer, String> getOpenIdConnectIssuers() {
        return openIdConnectIssuers;
    }

    @Override
    public Map<SubjectIssuer, String> getOpenIdConnectIssuersExtension() {
        return openIdConnectIssuersExtension;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultOAuthConfig that = (DefaultOAuthConfig) o;
        return Objects.equals(openIdConnectIssuers, that.openIdConnectIssuers)
                && Objects.equals(openIdConnectIssuersExtension, that.openIdConnectIssuersExtension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openIdConnectIssuers, openIdConnectIssuersExtension);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", openIdConnectIssuers=" + openIdConnectIssuers +
                ", openIdConnectIssuersExtension=" + openIdConnectIssuersExtension +
                "]";
    }

    private static class SubjectIssuerCollector
            implements Collector<Map.Entry<String, ConfigValue>, Map<SubjectIssuer, String>, Map<SubjectIssuer,
            String>> {

        private static SubjectIssuerCollector toSubjectIssuerMap() {
            return new SubjectIssuerCollector();
        }

        @Override
        public Supplier<Map<SubjectIssuer, String>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<SubjectIssuer, String>, Map.Entry<String, ConfigValue>> accumulator() {
            return (map, entry) -> map.put(SubjectIssuer.newInstance(entry.getKey()),
                    entry.getValue().unwrapped().toString());
        }

        @Override
        public BinaryOperator<Map<SubjectIssuer, String>> combiner() {
            return (left, right) -> Stream.concat(left.entrySet().stream(), right.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @Override
        public Function<Map<SubjectIssuer, String>, Map<SubjectIssuer, String>> finisher() {
            return map -> Collections.unmodifiableMap(new HashMap<>(map));
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.singleton(Characteristics.UNORDERED);
        }
    }

}
