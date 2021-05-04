/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;

import com.typesafe.config.Config;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

public final class DefaultSubjectIssuerConfig implements SubjectIssuerConfig {

    private final String issuer;

    private final List<String> authSubjectTemplates;

    private DefaultSubjectIssuerConfig(final ConfigWithFallback configWithFallback) {
        issuer = configWithFallback.getString(SubjectIssuerConfigValue.ISSUER.getConfigPath());
        authSubjectTemplates = configWithFallback.getStringList(SubjectIssuerConfigValue.AUTH_SUBJECTS.getConfigPath());
    }

    private DefaultSubjectIssuerConfig(final String issuer, final List<String> authSubjectTemplates) {
        this.issuer = issuer;
        this.authSubjectTemplates = authSubjectTemplates;
    }

    /**
     * Returns an instance of {@code DefaultSubjectIssuerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the config for the issuer at its current level.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSubjectIssuerConfig of(final Config config) {
        return new DefaultSubjectIssuerConfig(
                ConfigWithFallback.newInstance(config, SubjectIssuerConfigValue.values()));
    }

    /**
     * Returns a new SubjectIssuerConfig based on the provided strings.
     *
     * @param issuer       the issuer's endpoint {@code issuer}.
     * @param authSubjectTemplates list of authorizationsubject placeholder strings
     *                     {@code authSubjectTemplates}.
     * @return a new SubjectIssuerConfig.
     * @throws NullPointerException     if {@code issuer} or {@code authSubjectTemplates} is
     *                                  {@code null}.
     * @throws IllegalArgumentException if {@code issuer} or {@code authSubjectTemplates} is
     *                                  empty.
     */
    public static DefaultSubjectIssuerConfig of(final String issuer, final List<String> authSubjectTemplates) {
        checkNotNull(issuer, "issuer");
        argumentNotEmpty(authSubjectTemplates, "authSubjectTemplates");

        return new DefaultSubjectIssuerConfig(issuer, authSubjectTemplates);
    }

    public final String getIssuer() {
        return issuer;
    }

    public final List<String> getAuthorizationSubjectTemplates() {
        return authSubjectTemplates;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSubjectIssuerConfig that = (DefaultSubjectIssuerConfig) o;
        return Objects.equals(issuer, that.issuer) && authSubjectTemplates.equals(that.authSubjectTemplates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, authSubjectTemplates);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "issuer=" + issuer +
                ", authSubjectTemplates=" + authSubjectTemplates +
                "]";
    }
}
