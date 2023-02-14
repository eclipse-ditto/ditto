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
package org.eclipse.ditto.connectivity.service.messaging.hono;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Set;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Default implementation of {@link HonoConnectionFactory}.
 * This implementation uses {@link HonoConfig} to obtain the required properties for creating the Hono connection.
 */
public final class DefaultHonoConnectionFactory extends HonoConnectionFactory {

    private final HonoConfig honoConfig;

    private ConnectionId connectionId;

    /**
     * Constructs a {@code DefaultHonoConnectionFactory} for the specified arguments.
     *
     * @param actorSystem the actor system in which to load the factory.
     * @param config configuration properties for this factory.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public DefaultHonoConnectionFactory(final ActorSystem actorSystem, final Config config) {
        honoConfig = new DefaultHonoConfig(actorSystem);
    }

    @Override
    protected void preConversion(final Connection honoConnection) {
        connectionId = honoConnection.getId();
    }

    @Override
    public URI getBaseUri() {
        return honoConfig.getBaseUri();
    }

    @Override
    public boolean isValidateCertificates() {
        return honoConfig.isValidateCertificates();
    }

    @Override
    public HonoConfig.SaslMechanism getSaslMechanism() {
        return honoConfig.getSaslMechanism();
    }

    @Override
    public Set<URI> getBootstrapServerUris() {
        return honoConfig.getBootstrapServerUris();
    }

    @Override
    protected String getGroupId(final String suffix) {
        return suffix;
    }

    @Override
    protected UserPasswordCredentials getCredentials() {
        return honoConfig.getUserPasswordCredentials();
    }

    @Override
    protected String resolveSourceAddress(final HonoAddressAlias honoAddressAlias) {
        return MessageFormat.format("hono.{0}.{1}",
                honoAddressAlias.getAliasValue(), connectionId);
    }

    @Override
    protected String resolveTargetAddress(final HonoAddressAlias honoAddressAlias) {
        return MessageFormat.format("hono.{0}.{1}/'{{thing:id}}'",
                honoAddressAlias.getAliasValue(), connectionId);
    }

}
