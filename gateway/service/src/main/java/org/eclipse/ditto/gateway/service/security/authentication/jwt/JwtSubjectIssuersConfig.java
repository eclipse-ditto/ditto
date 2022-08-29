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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.policies.model.SubjectIssuer;

/**
 * Configuration for subject issuers.
 */
@Immutable
public final class JwtSubjectIssuersConfig {

    private final String protocolPrefix;
    private final Map<String, JwtSubjectIssuerConfig> subjectIssuerConfigMap;

    /**
     * Constructor.
     *
     * @param configItems the items (configurations for each subject issuer)
     * @param protocol the protocol prefix of all URIs of OAuth endpoints.
     */
    private JwtSubjectIssuersConfig(final Iterable<JwtSubjectIssuerConfig> configItems, final String protocol) {
        protocolPrefix = protocol + "://";
        requireNonNull(configItems);
        final Map<String, JwtSubjectIssuerConfig> modifiableSubjectIssuerConfigMap = new LinkedHashMap<>();

        configItems.forEach(configItem ->
                addConfigToMap(configItem, modifiableSubjectIssuerConfigMap, protocolPrefix));
        subjectIssuerConfigMap = Collections.unmodifiableMap(modifiableSubjectIssuerConfigMap);
    }

    public static JwtSubjectIssuersConfig fromJwtSubjectIssuerConfigs(
            final Iterable<JwtSubjectIssuerConfig> configItems) {
        return new JwtSubjectIssuersConfig(configItems,
                (String) OAuthConfig.OAuthConfigValue.PROTOCOL.getDefaultValue());
    }

    public static JwtSubjectIssuersConfig fromOAuthConfig(final OAuthConfig config) {
        final Set<JwtSubjectIssuerConfig> configItems =
                // merge the default and extension config
                Stream.concat(config.getOpenIdConnectIssuers().entrySet().stream(),
                        config.getOpenIdConnectIssuersExtension().entrySet().stream())
                        .map(entry -> new JwtSubjectIssuerConfig(entry.getKey(), entry.getValue().getIssuers(),
                                entry.getValue().getAuthorizationSubjectTemplates()))
                        .collect(Collectors.toSet());
        return new JwtSubjectIssuersConfig(configItems, config.getProtocol());
    }

    private static void addConfigToMap(final JwtSubjectIssuerConfig config,
            final Map<String, JwtSubjectIssuerConfig> map,
            final String protocolPrefix) {

        config.getIssuers()
                .forEach(issuer -> {
                    map.put(issuer, config);
                    map.put(protocolPrefix + issuer, config);
                });
    }

    public String getProtocolPrefix() {
        return protocolPrefix;
    }

    /**
     * Gets the configuration item for the given issuer.
     *
     * @param issuer the issuer
     * @return the configuration for the given issuer, or an empty {@link java.util.Optional} if no configuration is provided
     * for this issuer
     */
    public Optional<JwtSubjectIssuerConfig> getConfigItem(final String issuer) {
        return getConfigItemByIssuer(issuer)
                .or(() -> Optional.ofNullable(subjectIssuerConfigMap.get(issuer)));
    }

    private Optional<JwtSubjectIssuerConfig> getConfigItemByIssuer(final String issuer) {
        return subjectIssuerConfigMap.values()
                .stream()
                .filter(jwtSubjectIssuerConfig -> jwtSubjectIssuerConfig.getIssuers().stream()
                        .anyMatch(configuredIssuer -> configuredIssuer.equals(issuer) ||
                                protocolPrefix.concat(configuredIssuer).equals(issuer))
                )
                .findFirst();
    }

    /**
     * Gets a collection of all configuration items.
     *
     * @return the configuration items
     */
    public Collection<JwtSubjectIssuerConfig> getConfigItems() {
        return subjectIssuerConfigMap.values();
    }

    /**
     * Gets a collection of all configuration items associated to the given {@code SubjectIssuer}.
     *
     * @param subjectIssuer the issuer.
     * @return the configuration items.
     * @throws NullPointerException if {@code subjectIssuer} is {@code null}.
     */
    public Collection<JwtSubjectIssuerConfig> getConfigItems(final SubjectIssuer subjectIssuer) {
        checkNotNull(subjectIssuer);

        return subjectIssuerConfigMap.values().stream()
                .filter(jwtSubjectIssuerConfig -> jwtSubjectIssuerConfig.getSubjectIssuer().equals(subjectIssuer))
                .toList();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JwtSubjectIssuersConfig that = (JwtSubjectIssuersConfig) o;
        return Objects.equals(protocolPrefix, that.protocolPrefix) &&
                Objects.equals(subjectIssuerConfigMap, that.subjectIssuerConfigMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolPrefix, subjectIssuerConfigMap);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[protocolPrefix=" + protocolPrefix +
                ", subjectIssuerConfigMap=" + subjectIssuerConfigMap +
                ']';
    }
}
