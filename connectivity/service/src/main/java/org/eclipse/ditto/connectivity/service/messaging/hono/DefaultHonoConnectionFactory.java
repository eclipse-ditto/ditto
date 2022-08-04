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
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;

import akka.actor.ActorSystem;

/**
 * Default implementation of {@link HonoConnectionFactory}.
 * This implementation uses {@link HonoConfig} to obtain the required properties for creating the Hono connection.
 *
 * @since 3.0.0
 */
public final class DefaultHonoConnectionFactory extends HonoConnectionFactory {

    private final HonoConfig honoConfig;

    private DefaultHonoConnectionFactory(final HonoConfig honoConfig, final Connection connection) {
        super(connection);
        this.honoConfig = honoConfig;
    }

    /**
     * Returns a new instance of {@code DefaultHonoConnectionFactory} for the specified arguments.
     *
     * @param actorSystem the actor system that is used to obtain the HonoConfig.
     * @param connection the connection that serves as base for the Hono connection this factory returns.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the type of {@code connection} is not {@link ConnectionType#HONO};
     */
    public static DefaultHonoConnectionFactory newInstance(final ActorSystem actorSystem, final Connection connection) {
        return new DefaultHonoConnectionFactory(new DefaultHonoConfig(actorSystem), connection);
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
    protected String getGroupId(final Connection connection) {
        return connection.getId().toString();
    }

    @Override
    protected UserPasswordCredentials getCredentials() {
        return honoConfig.getUserPasswordCredentials();
    }

    @Override
    protected String resolveSourceAddress(final HonoAddressAlias honoAddressAlias) {
        return MessageFormat.format("hono.{0}", honoAddressAlias.getAliasValue());
    }

    @Override
    protected String resolveTargetAddress(final HonoAddressAlias honoAddressAlias) {
        return MessageFormat.format("hono.{0}/'{{thing:id}}'", honoAddressAlias.getAliasValue());
    }

}
