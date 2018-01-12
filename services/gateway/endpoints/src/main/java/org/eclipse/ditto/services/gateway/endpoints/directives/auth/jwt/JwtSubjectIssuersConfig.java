/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

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

    private final Map<String, JwtSubjectIssuerConfig> subjectIssuerConfigMap;

    /**
     * Constructor.
     *
     * @param configItems the items (configurations for each UNKNOWN issuer)
     */
    public JwtSubjectIssuersConfig(final Iterable<JwtSubjectIssuerConfig> configItems) {
        requireNonNull(configItems);
        final Map<String, JwtSubjectIssuerConfig> modifiableSubjectIssuerConfigMap = new HashMap<>();
        configItems.forEach(configItem ->
                modifiableSubjectIssuerConfigMap.put(configItem.getJwtIssuer(), configItem));
        subjectIssuerConfigMap = Collections.unmodifiableMap(modifiableSubjectIssuerConfigMap);
    }

    /**
     * Gets the configuration item for the given issuer.
     *
     * @param jwtIssuer the issuer
     * @return the configuration for the given issuer, or an empty {@link Optional} if no configuration is provided
     * for this issuer
     */
    public Optional<JwtSubjectIssuerConfig> getConfigItem(final String jwtIssuer) {
        return Optional.ofNullable(subjectIssuerConfigMap.get(jwtIssuer));
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
                .filter(jwtSubjectIssuerConfig -> subjectIssuer.equals(jwtSubjectIssuerConfig.getSubjectIssuer()))
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
