/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * A mutable builder for a {@link Source} with a fluent API.
 *
 * @param <T> the type that is returned by builder methods
 */
public interface SourceBuilder<T extends SourceBuilder> {

    /**
     * Sets the addresses.
     *
     * @param addresses the addresses
     * @return this builder
     */
    T addresses(Set<String> addresses);

    /**
     * Adds an address.
     *
     * @param address the address
     * @return this builder
     */
    T address(String address);

    /**
     * Sets the consumer count.
     *
     * @param consumerCount the consumer count
     * @return this builder
     */
    T consumerCount(final int consumerCount);

    /**
     * Sets the index of the source inside a connection.
     *
     * @param index the index
     * @return this builder
     */
    T index(final int index);

    /**
     * Sets the {@link AuthorizationContext}.
     *
     * @param authorizationContext the authorization context
     * @return this builder
     */
    T authorizationContext(final AuthorizationContext authorizationContext);

    /**
     * Sets the {@link Enforcement} options, may be null if enforcement is not enabled.
     *
     * @param enforcement the enforcement
     * @return this builder
     */
    T enforcement(@Nullable final Enforcement enforcement);

    /**
     * Build the source instance.
     *
     * @return the new source instance
     */
    Source build();

}
