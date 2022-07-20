/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Default implementation for {@link HonoConfig}.
 */
@Immutable
public final class DefaultHonoConfig implements HonoConfig {

    private final URI baseUri;
    private final boolean validateCertificates;
    private final SaslMechanism saslMechanism;
    private final Set<URI> bootstrapServerUris;
    private final UserPasswordCredentials credentials;


    /**
     * Constructs a {@code DefaultHonoConfig} for the specified ActorSystem.
     *
     * @param actorSystem the actor system that provides the overall core config.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public DefaultHonoConfig(final ActorSystem actorSystem) {
        this(ConfigWithFallback.newInstance(checkNotNull(actorSystem, "actorSystem").settings().config(),
                PREFIX,
                HonoConfigValue.values()));
    }

    private DefaultHonoConfig(final ScopedConfig scopedConfig) {
        baseUri = getBaseUriOrThrow(scopedConfig);
        validateCertificates = scopedConfig.getBoolean(HonoConfigValue.VALIDATE_CERTIFICATES.getConfigPath());
        saslMechanism = scopedConfig.getEnum(SaslMechanism.class, HonoConfigValue.SASL_MECHANISM.getConfigPath());
        bootstrapServerUris = Collections.unmodifiableSet(getBootstrapServerUrisOrThrow(scopedConfig));
        credentials = UserPasswordCredentials.newInstance(
                scopedConfig.getString(HonoConfigValue.USERNAME.getConfigPath()),
                scopedConfig.getString(HonoConfigValue.PASSWORD.getConfigPath())
        );
    }

    private static URI getBaseUriOrThrow(final Config scopedConfig) {
        final var configPath = HonoConfigValue.BASE_URI.getConfigPath();
        try {
            return new URI(scopedConfig.getString(configPath));
        } catch (final URISyntaxException e) {
            throw new DittoConfigError(
                    MessageFormat.format("The string value at <{0}> is not a {1}: {2}",
                            configPath,
                            URI.class.getSimpleName(),
                            e.getMessage()),
                    e
            );
        }
    }

    private static Set<URI> getBootstrapServerUrisOrThrow(final Config scopedConfig) {
        final var configPath = HonoConfigValue.BOOTSTRAP_SERVERS.getConfigPath();
        final BiFunction<Integer, String, URI> getUriOrThrow = (index, uriString) -> {
            try {
                return new URI(uriString.trim());
            } catch (final URISyntaxException e) {
                throw new DittoConfigError(
                        MessageFormat.format("The string at index <{0}> for key <{1}> is not a valid URI: {2}",
                                index,
                                configPath,
                                e.getMessage()),
                        e
                );
            }
        };

        final var bootstrapServersString = scopedConfig.getString(configPath);
        final var bootstrapServerUriStrings = bootstrapServersString.split(",");
        final Set<URI> result = new LinkedHashSet<>(bootstrapServerUriStrings.length);
        for (var i = 0; i < bootstrapServerUriStrings.length; i++) {
            result.add(getUriOrThrow.apply(i, bootstrapServerUriStrings[i]));
        }
        return result;
    }

    @Override
    public URI getBaseUri() {
        return baseUri;
    }

    @Override
    public boolean isValidateCertificates() {
        return validateCertificates;
    }

    @Override
    public SaslMechanism getSaslMechanism() {
        return saslMechanism;
    }

    @Override
    public Set<URI> getBootstrapServerUris() {
        return bootstrapServerUris;
    }

    @Override
    public UserPasswordCredentials getUserPasswordCredentials() {
        return credentials;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (DefaultHonoConfig) o;
        return Objects.equals(baseUri, that.baseUri)
                && Objects.equals(validateCertificates, that.validateCertificates)
                && Objects.equals(saslMechanism, that.saslMechanism)
                && Objects.equals(bootstrapServerUris, that.bootstrapServerUris)
                && Objects.equals(credentials, that.credentials);

    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUri, validateCertificates, saslMechanism, bootstrapServerUris, credentials);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "baseUri=" + baseUri +
                ", validateCertificates=" + validateCertificates +
                ", saslMechanism=" + saslMechanism +
                ", bootstrapServers=" + bootstrapServerUris +
                "]";
    }

}