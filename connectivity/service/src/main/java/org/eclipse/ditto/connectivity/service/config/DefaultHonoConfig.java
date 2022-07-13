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

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.api.HonoConfig;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Configuration class providing parameters for connection type 'Hono' in Ditto from static configuration
 */
public final class DefaultHonoConfig implements HonoConfig {

    private final URI baseUri;
    private final boolean validateCertificates;
    private final SaslMechanism saslMechanism;
    private final String bootstrapServers;

    private final UserPasswordCredentials credentials;

    public DefaultHonoConfig(final ActorSystem actorSystem) {
        ConditionChecker.checkNotNull(actorSystem, "actorSystem");
        final Config config = actorSystem.settings().config().getConfig(PREFIX);

        this.baseUri = HonoConfig.getUri(config.getString(HonoConfigValue.BASE_URI.getConfigPath()));
        this.validateCertificates = config.getBoolean(HonoConfigValue.VALIDATE_CERTIFICATES.getConfigPath());
        this.saslMechanism = config.getEnum(SaslMechanism.class, HonoConfigValue.SASL_MECHANISM.getConfigPath());
        this.bootstrapServers = config.getString(HonoConfigValue.BOOTSTRAP_SERVERS.getConfigPath());
        // Validate bootstrap servers
        Arrays.stream(this.bootstrapServers.split(",")).forEach(HonoConfig::getUri);

        this.credentials = UserPasswordCredentials.newInstance(
                config.getString(HonoConfigValue.USERNAME.getConfigPath()),
                config.getString(HonoConfigValue.PASSWORD.getConfigPath()));
    }

    /**
     * @return Base URI, including port number
     */
    @Override
    public URI getBaseUri() {
        return baseUri;
    }

    /**
     * @return validateCertificates boolean property
     */
    @Override
    public boolean isValidateCertificates() {
        return validateCertificates;
    }

    /**
     * @return SASL mechanism property
     */
    @Override
    public SaslMechanism getSaslMechanism() {
        return saslMechanism;
    }

    /**
     * @return Bootstrap servers address(es)
     */
    @Override
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Gets credentials for hono messaging
     * @param connectionId The connection ID of the connection
     * @return {@link org.eclipse.ditto.connectivity.model.UserPasswordCredentials} for hub messaging
     */
    @Override
    public UserPasswordCredentials getCredentials(final ConnectionId connectionId) {
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
        final DefaultHonoConfig that = (DefaultHonoConfig) o;
        return Objects.equals(baseUri, that.baseUri)
                && Objects.equals(validateCertificates, that.validateCertificates)
                && Objects.equals(saslMechanism, that.saslMechanism)
                && Objects.equals(bootstrapServers, that.bootstrapServers)
                && Objects.equals(credentials, that.credentials);

    }
    @Override
    public int hashCode() {
        return Objects.hash(baseUri, validateCertificates, saslMechanism, bootstrapServers, credentials);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "baseUri=" + baseUri +
                ", validateCertificates=" + validateCertificates +
                ", saslMechanism=" + saslMechanism +
                ", bootstrapServers=" + bootstrapServers +
                "]";
    }

}