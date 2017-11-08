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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.ditto.model.policies.SubjectIssuer;

/**
 * Configuration for UNKNOWN issuers.
 */
public final class JwtSubjectIssuersConfig {
    private final Map<SubjectIssuer, JwtSubjectIssuerConfig> subjectIssuerConfigMap;

    /**
     * Constructor.
     *
     * @param configItems the items (configurations for each UNKNOWN issuer)
     */
    public JwtSubjectIssuersConfig(final Iterable<JwtSubjectIssuerConfig> configItems) {
        requireNonNull(configItems);
        final Map<SubjectIssuer, JwtSubjectIssuerConfig> modifiableSubjectIssuerConfigMap = new HashMap<>();
        configItems.forEach(configItem ->
                modifiableSubjectIssuerConfigMap.put(configItem.getSubjectIssuer(), configItem));
        subjectIssuerConfigMap = Collections.unmodifiableMap(modifiableSubjectIssuerConfigMap);
    }

    /**
     * Gets the configuration item for the given issuer.
     * @param subjectIssuer the issuer
     *
     * @return the configuration for the given issuer, or an empty {@link Optional} if no configuration is provided
     * for this issuer
     */
    public Optional<JwtSubjectIssuerConfig> getConfigItem(final SubjectIssuer subjectIssuer) {
        return Optional.ofNullable(subjectIssuerConfigMap.get(subjectIssuer));
    }

    /**
     * Gets a collection of all configuration items.
     *
     * @return the configuration items
     */
    public Collection<JwtSubjectIssuerConfig> getConfigItems() {
        return subjectIssuerConfigMap.values();
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
