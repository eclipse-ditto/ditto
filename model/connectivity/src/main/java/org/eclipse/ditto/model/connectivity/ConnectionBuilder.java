/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

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
    ConnectionBuilder id(String id);

    /**
     * Sets the name to use in the {@code Connection}.
     *
     * @param name the name.
     * @return this builder to allow method chaining.
     */
    ConnectionBuilder name(@Nullable String name);

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
    ConnectionBuilder connectionStatus(ConnectionStatus connectionStatus);

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
    ConnectionBuilder targets(Set<Target> targets);

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
     * Builds a new {@link Connection}.
     *
     * @return the new {@link Connection}
     */
    Connection build();

}
