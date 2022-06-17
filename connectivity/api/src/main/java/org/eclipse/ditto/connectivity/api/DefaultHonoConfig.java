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
package org.eclipse.ditto.connectivity.api;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.HonoAddressAliasValues;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.Credentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Configuration class providing parameters for connection type 'Hono' in Ditto from static configuration
 */
public final class DefaultHonoConfig implements HonoConfig {

    private final String baseUri;
    private final SaslMechanism saslMechanism;
    private final String bootstrapServers;

    private final HonoAddressAliasValues honoAddressAliasValues;

    private final Credentials credentials;

    public DefaultHonoConfig(final ActorSystem actorSystem) {
        ConditionChecker.checkNotNull(actorSystem, "actorSystem");
        final Config config = actorSystem.settings().config().getConfig(PREFIX);

        this.baseUri = config.getString(ConfigValues.BASE_URI.getConfigPath());
        this.saslMechanism = config.getEnum(SaslMechanism.class, ConfigValues.SASL_MECHANISM.getConfigPath());
        this.bootstrapServers = config.getString(ConfigValues.BOOTSTRAP_SERVERS.getConfigPath());

        honoAddressAliasValues = HonoAddressAliasValues.newInstance(
                config.getString(ConfigValues.TELEMETRY_ADDRESS.getConfigPath()),
                config.getString(ConfigValues.EVENT_ADDRESS.getConfigPath()),
                config.getString(ConfigValues.COMMAND_AND_CONTROL_ADDRESS.getConfigPath()),
                config.getString(ConfigValues.COMMAND_RESPONSE_ADDRESS.getConfigPath()));

        this.credentials = UserPasswordCredentials.newInstance(
                config.getString(ConfigValues.USERNAME.getConfigPath()),
                config.getString(ConfigValues.PASSWORD.getConfigPath()));
    }

    @Override
    public String getBaseUri() {
        return baseUri;
    }

    @Override
    public SaslMechanism getSaslMechanism() {
        return saslMechanism;
    }

    @Override
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    @Override
    public HonoAddressAliasValues getAddressAliases(final ConnectionId connectionId) {
        return honoAddressAliasValues;
    }

    @Override
    public Credentials getCredentials(final ConnectionId connectionId) {
        return credentials;
    }

}