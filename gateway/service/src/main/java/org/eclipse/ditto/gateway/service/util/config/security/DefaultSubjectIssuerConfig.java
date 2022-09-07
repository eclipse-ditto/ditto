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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the SubjectIssuer config.
 * It is instantiated for each {@code openid-connect-issuers} entry containing issuers and auth-subject templates.
 */
public final class DefaultSubjectIssuerConfig implements SubjectIssuerConfig {

    private final List<String> issuers;

    private final List<String> authSubjectTemplates;

    private DefaultSubjectIssuerConfig(final String issuerConfigKey, final ConfigWithFallback configWithFallback) {
        final List<String> issuersList =
                configWithFallback.getStringList(SubjectIssuerConfigValue.ISSUERS.getConfigPath());
        if (!issuersList.isEmpty()) {
            issuers = issuersList;
        } else {
            final String singleIssuer = configWithFallback.getString(SubjectIssuerConfigValue.ISSUER.getConfigPath());
            if (!singleIssuer.isBlank()) {
                issuers = List.of(singleIssuer);
            } else {
                throw new DittoConfigError("Neither '" + SubjectIssuerConfigValue.ISSUERS.getConfigPath() +
                        "' nor '" + SubjectIssuerConfigValue.ISSUER.getConfigPath() + "' were configured " +
                        "for openid-connect issuer: <" + issuerConfigKey + ">");
            }
        }
        authSubjectTemplates = configWithFallback.getStringList(SubjectIssuerConfigValue.AUTH_SUBJECTS.getConfigPath());
    }

    private DefaultSubjectIssuerConfig(final Collection<String> issuers,
            final Collection<String> authSubjectTemplates) {
        this.issuers = Collections.unmodifiableList(new ArrayList<>(issuers));
        this.authSubjectTemplates = Collections.unmodifiableList(new ArrayList<>(authSubjectTemplates));
    }

    /**
     * Returns an instance of {@code DefaultSubjectIssuerConfig} based on the settings of the specified Config.
     *
     * @param key the key of the open-id-issuer config passed in the {@code config}.
     * @param config is supposed to provide the config for the issuer at its current level.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSubjectIssuerConfig of(final String key, final Config config) {
        return new DefaultSubjectIssuerConfig(key,
                ConfigWithFallback.newInstance(config, SubjectIssuerConfigValue.values()));
    }

    /**
     * Returns a new SubjectIssuerConfig based on the provided strings.
     *
     * @param issuers the list of issuers' endpoint {@code issuers}.
     * @param authSubjectTemplates list of authorizationsubject placeholder strings.
     * @return a new SubjectIssuerConfig.
     * @throws NullPointerException if {@code issuers} or {@code authSubjectTemplates} is {@code null}.
     * @throws IllegalArgumentException if {@code issuers} or {@code authSubjectTemplates} is empty.
     */
    public static DefaultSubjectIssuerConfig of(final Collection<String> issuers,
            final Collection<String> authSubjectTemplates) {
        checkNotNull(issuers, "issuers");
        argumentNotEmpty(authSubjectTemplates, "authSubjectTemplates");

        return new DefaultSubjectIssuerConfig(issuers, authSubjectTemplates);
    }

    public List<String> getIssuers() {
        return issuers;
    }

    public List<String> getAuthorizationSubjectTemplates() {
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
        return Objects.equals(issuers, that.issuers) && authSubjectTemplates.equals(that.authSubjectTemplates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuers, authSubjectTemplates);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "issuers=" + issuers +
                ", authSubjectTemplates=" + authSubjectTemplates +
                "]";
    }
}
