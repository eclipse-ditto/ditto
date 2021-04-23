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
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;

/**
 * A mutable builder for a {@link Source} with a fluent API.
 *
 * @param <T> the type that is returned by builder methods
 */
public interface SourceBuilder<T extends SourceBuilder<?>> {

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
    T consumerCount(int consumerCount);

    /**
     * Sets the index of the source inside a connection.
     *
     * @param index the index
     * @return this builder
     */
    T index(int index);

    /**
     * Sets the qos of the source inside a connection.
     *
     * @param qos the qos
     * @return this builder
     */
    T qos(@Nullable Integer qos);

    /**
     * Sets the {@link AuthorizationContext}.
     *
     * @param authorizationContext the authorization context
     * @return this builder
     */
    T authorizationContext(AuthorizationContext authorizationContext);

    /**
     * Sets the {@link Enforcement} options, may be null if enforcement is not enabled.
     *
     * @param enforcement the enforcement
     * @return this builder
     */
    T enforcement(@Nullable Enforcement enforcement);

    /**
     * Sets the {@link AcknowledgementRequest}s that are requested from messages consumed
     * in the built source with an optionally applied filter.
     *
     * @param acknowledgementRequests the acknowledgement requests with an optional filter
     * @return this builder
     * @since 1.2.0
     */
    T acknowledgementRequests(@Nullable FilteredAcknowledgementRequest acknowledgementRequests);


    /**
     * Sets the {@link HeaderMapping}, may be null if headerMapping is not enabled.
     *
     * @param headerMapping the headerMapping
     * @return this builder
     */
    T headerMapping(@Nullable HeaderMapping headerMapping);

    /**
     * Sets the payload mappings for this source.
     *
     * @param payloadMapping the payload mappings for this source
     * @return this builder
     */
    T payloadMapping(PayloadMapping payloadMapping);

    /**
     * Set the reply target.
     *
     * @param replyTarget the new reply-target, or null to remove the current reply-target.
     * @return this builder.
     */
    T replyTarget(@Nullable ReplyTarget replyTarget);

    /**
     * Set whether the reply-target is enabled.
     *
     * @param replyTargetEnabled whether the reply-target is enabled.
     * @return this builder.
     */
    T replyTargetEnabled(boolean replyTargetEnabled);

    /**
     * Set what labels are allowed for incoming acknowledgements to this source.
     *
     * @param acknowledgementLabels acknowledgement labels to declare.
     * @return this builder.
     * @since 1.4.0
     */
    T declaredAcknowledgementLabels(Set<AcknowledgementLabel> acknowledgementLabels);

    /**
     * Build the source instance.
     *
     * @return the new source instance
     */
    Source build();

}
