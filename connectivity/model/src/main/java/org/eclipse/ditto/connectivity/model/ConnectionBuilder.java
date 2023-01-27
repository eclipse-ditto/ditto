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
package org.eclipse.ditto.connectivity.model;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;

/**
 * A mutable builder for a {@link Connection} with a fluent API.
 */
public interface ConnectionBuilder {

    /**
     * Sets the identifier to use in the {@code Connection}.
     *
     * @param id the identifier.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code id} is {@code null}.
     */
    ConnectionBuilder id(ConnectionId id);

    /**
     * Sets the name to use in the {@code Connection}.
     *
     * @param name the name.
     * @return this builder to allow method chaining.
     */
    ConnectionBuilder name(@Nullable String name);

    /**
     * Sets the connection credentials.
     *
     * @param credentials the credentials.
     * @return this builder.
     */
    ConnectionBuilder credentials(@Nullable Credentials credentials);

    /**
     * Sets the connection credentials in JSON representation.
     *
     * @param jsonObject credentials in JSON representation.
     * @return this builder.
     */
    default ConnectionBuilder credentialsFromJson(final JsonObject jsonObject) {
        return credentials(Credentials.fromJson(jsonObject));
    }

    /**
     * Set the trusted certificates.
     *
     * @param trustedCertificates the trusted certificates
     * @return this builder
     */
    ConnectionBuilder trustedCertificates(@Nullable final String trustedCertificates);

    /**
     * Sets the URI to use in the {@code Connection}.
     *
     * @param uri the URI.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code uri} is {@code null}.
     */
    ConnectionBuilder uri(String uri);

    /**
     * Sets the ConnectionStatus to use in the {@code Connection}.
     *
     * @param connectionStatus the ConnectionStatus.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    ConnectionBuilder connectionStatus(ConnectivityStatus connectionStatus);

    /**
     * Enable/disable fail-over for the {@code Connection}.
     *
     * @param failoverEnabled if fail-over is enabled for this connection (default {@code true}).
     * @return this builder to allow method chaining.
     */
    ConnectionBuilder failoverEnabled(boolean failoverEnabled);

    /**
     * Enable/disable validation of certificates for the {@code Connection}.
     *
     * @param validateCertificate if server certificates are validated (default {@code true}).
     * @return this builder to allow method chaining.
     */
    ConnectionBuilder validateCertificate(boolean validateCertificate);

    /**
     * Set the command processor pool size for the {@code Connection}.
     *
     * @param processorPoolSize number of command processor actors that will be used at max (default {@code 5}).
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if {@code processorPoolSize} is not positive.
     */
    ConnectionBuilder processorPoolSize(int processorPoolSize);

    /**
     * Adds additional sources to the connection.
     *
     * @param sources the sources that are added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code sources} is {@code null}.
     */
    ConnectionBuilder sources(List<Source> sources);

    /**
     * Adds additional targets to the connection.
     *
     * @param targets the targets that are added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code targets} is {@code null}.
     */
    ConnectionBuilder targets(List<Target> targets);

    /**
     * Set sources of connection.
     *
     * @param sources the new sources.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code sources} is {@code null}.
     */
    ConnectionBuilder setSources(List<Source> sources);

    /**
     * Set targets to the connection.
     *
     * @param targets the new targets.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code targets} is {@code null}.
     */
    ConnectionBuilder setTargets(List<Target> targets);

    /**
     * Sets how many clients on different cluster nodes should establish the {@code Connection}.
     * <p>
     * If greater than 1, the connection is created in a HA mode, running on at least 2 cluster nodes.
     * </p>
     *
     * @param clientCount the client count to set.
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if {@code clientCount} is not positive.
     */
    ConnectionBuilder clientCount(int clientCount);

    /**
     * Adds configuration which is only applicable for a specific {@code ConnectionType}.
     *
     * @param specificConfig the ConnectionType specific configuration to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code specificConfig} is {@code null}.
     */
    ConnectionBuilder specificConfig(Map<String, String> specificConfig);

    /**
     * Sets the MappingContext to apply for the connection containing either JavaScript scripts or a custom
     * implementation in Java mapping from external messages to internal Ditto Protocol messages.
     *
     * @param mappingContext the MappingContext to apply for the connection.
     * @return this builder to allow method chaining.
     */
    ConnectionBuilder mappingContext(@Nullable MappingContext mappingContext);

    /**
     * Sets the tags of the {@code Connection}.
     *
     * @param tags the tags to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code tags} is {@code null}.
     */
    ConnectionBuilder tags(Collection<String> tags);

    /**
     * Sets a tag of the {@code Connection}.
     *
     * @param tag the tag to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code tag} is {@code null}.
     */
    ConnectionBuilder tag(String tag);

    /**
     * Sets the {@link ConnectionLifecycle} of the connection.
     *
     * @param lifecycle the connection lifecycle
     * @return this builder
     */
    ConnectionBuilder lifecycle(@Nullable ConnectionLifecycle lifecycle);

    /**
     * Sets the given revision number to this builder.
     *
     * @param revisionNumber the revision number to be set.
     * @return this builder to allow method chaining.
     * @since 3.2.0
     */
    default ConnectionBuilder revision(final long revisionNumber) {
        return revision(ConnectionRevision.newInstance(revisionNumber));
    }

    /**
     * Sets the {@link ConnectionRevision} of the connection.
     *
     * @param revision the connection revision
     * @return this builder
     * @since 3.2.0
     */
    ConnectionBuilder revision(@Nullable ConnectionRevision revision);

    /**
     * Sets the given modified timestamp to this builder.
     *
     * @param modified the timestamp to be set.
     * @return this builder to allow method chaining.
     * @since 3.2.0
     */
    ConnectionBuilder modified(@Nullable Instant modified);

    /**
     * Sets the given created timestamp to this builder.
     *
     * @param created the created timestamp to be set.
     * @return this builder to allow method chaining.
     * @since 3.2.0
     */
    ConnectionBuilder created(@Nullable Instant created);

    /**
     * Sets the {@link SshTunnel} of the connection.
     *
     * @param sshTunnel the connection ssh tunnel
     * @return this builder
     *
     * @since 2.0.0
     */
    ConnectionBuilder sshTunnel(@Nullable SshTunnel sshTunnel);

    /**
     * Sets the payload mapping definition of the connection.
     *
     * @param payloadMappingDefinition the payload mapping definition.
     * @return this builder
     */
    ConnectionBuilder payloadMappingDefinition(PayloadMappingDefinition payloadMappingDefinition);

    /**
     * Builds a new {@link Connection}.
     *
     * @return the new {@link Connection}
     */
    Connection build();

}
