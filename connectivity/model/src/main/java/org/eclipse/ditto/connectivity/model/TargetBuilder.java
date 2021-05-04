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

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;

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
     * Sets the label of an acknowledgement which should automatically be issued by this target based on the technical
     * settlement/ACK the connection channel provides.
     *
     * @param acknowledgementLabel the label of the automatically issued acknowledgement
     * @return this builder
     * @since 1.2.0
     */
    TargetBuilder issuedAcknowledgementLabel(@Nullable AcknowledgementLabel acknowledgementLabel);

    /**
     * Sets the {@link HeaderMapping}, may be null if headerMapping is not enabled.
     *
     * @param headerMapping the headerMapping
     * @return this builder
     */
    TargetBuilder headerMapping(@Nullable HeaderMapping headerMapping);

    /**
     * Sets the payload mappings for the target.
     *
     * @param payloadMapping the payload mappings for this target
     * @return this builder
     */
    TargetBuilder payloadMapping(PayloadMapping payloadMapping);

    /**
     * Build the {@link Target} instance.
     *
     * @return the new {@link Target} instance
     */
    Target build();

}
