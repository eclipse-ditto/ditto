/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * A builder for a {@link Target} with a fluent API.
 */
public interface TargetBuilder {

    /**
     * Sets the address.
     *
     * @param address the address
     * @return this builder
     */
    TargetBuilder address(String address);

    /**
     * Sets the originalAddress.
     *
     * @param address the originalAddress
     * @return this builder
     */
    TargetBuilder originalAddress(String address);

    /**
     * Sets the qos of the target inside a connection.
     *
     * @param qos the qos
     * @return this builder
     */
    TargetBuilder qos(@Nullable Integer qos);

    /**
     * Sets the {@link AuthorizationContext}.
     *
     * @param authorizationContext the authorization context
     * @return this builder
     */
    TargetBuilder authorizationContext(AuthorizationContext authorizationContext);

    /**
     * Sets the {@link FilteredTopic}s.
     *
     * @param topics the topics
     * @return this builder
     */
    TargetBuilder topics(Set<FilteredTopic> topics);

    /**
     * Sets the {@link FilteredTopic}s.
     *
     * @param requiredTopic the topics
     * @param additionalTopics the topics
     * @return this builder
     */
    TargetBuilder topics(FilteredTopic requiredTopic, FilteredTopic... additionalTopics);

    /**
     * Sets the {@link Topic}s.
     *
     * @param requiredTopic the topics
     * @param additionalTopics the topics
     * @return this builder
     */
    TargetBuilder topics(final Topic requiredTopic, final Topic... additionalTopics);

    /**
     * Sets the {@link HeaderMapping}, may be null if headerMapping is not enabled.
     *
     * @param headerMapping the headerMapping
     * @return this builder
     */
    TargetBuilder headerMapping(@Nullable HeaderMapping headerMapping);

    /**
     * Build the {@link Target} instance.
     *
     * @return the new {@link Target} instance
     */
    Target build();

}
