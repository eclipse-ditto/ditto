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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.policies.SubjectIssuer;

/**
 * Configuration for subject issuers.
 */
@Immutable
public final class JwtSubjectIssuersConfig {

    private static final String HTTPS = "https://";

    private final Map<String, JwtSubjectIssuerConfig> subjectIssuerConfigMap;

    /**
     * Constructor.
     *
     * @param configItems the items (configurations for each subject issuer)
     */
    public JwtSubjectIssuersConfig(final Iterable<JwtSubjectIssuerConfig> configItems) {
        requireNonNull(configItems);
        final Map<String, JwtSubjectIssuerConfig> modifiableSubjectIssuerConfigMap = new HashMap<>();

        configItems.forEach(configItem -> addConfigToMap(configItem, modifiableSubjectIssuerConfigMap));
        subjectIssuerConfigMap = Collections.unmodifiableMap(modifiableSubjectIssuerConfigMap);
    }

    private static void addConfigToMap(final JwtSubjectIssuerConfig config,
            final Map<String, JwtSubjectIssuerConfig> map) {
        map.put(config.getIssuer(), config);
        map.put(HTTPS + config.getIssuer(), config);
    }

    /**
     * Gets the configuration item for the given issuer.
     *
     * @param issuer the issuer
     * @return the configuration for the given issuer, or an empty {@link Optional} if no configuration is provided
     * for this issuer
     */
    public Optional<JwtSubjectIssuerConfig> getConfigItem(final String issuer) {
        return Optional.ofNullable(subjectIssuerConfigMap.get(issuer));
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
     * @throws java.lang.NullPointerException if {@code subjectIssuer} is {@code null}.
     */
    public Collection<JwtSubjectIssuerConfig> getConfigItems(final SubjectIssuer subjectIssuer) {
        checkNotNull(subjectIssuer);

        return subjectIssuerConfigMap.values().stream()
                .filter(jwtSubjectIssuerConfig -> jwtSubjectIssuerConfig.getSubjectIssuer().equals(subjectIssuer))
                .collect(Collectors.toList());
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
        return Objects.equals(subjectIssuerConfigMap, that.subjectIssuerConfigMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectIssuerConfigMap);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [subjectIssuerConfigMap=" + subjectIssuerConfigMap + ']';
    }
}
