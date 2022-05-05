/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.MappedInboundExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;

import akka.actor.ActorRef;

/**
 * Outcome of mapping one inbound external message.
 */
public final class InboundMappingOutcomes {

    private final List<MappingOutcome<MappedInboundExternalMessage>> outcomes;
    private final ExternalMessage externalMessage;
    @Nullable private final Exception error;
    private final ActorRef sender;

    private InboundMappingOutcomes(final List<MappingOutcome<MappedInboundExternalMessage>> outcomes,
            final ExternalMessage externalMessage,
            @Nullable final Exception error,
            final ActorRef sender) {
        this.outcomes = outcomes;
        this.externalMessage = externalMessage;
        this.error = error;
        this.sender = sender;
    }

    /**
     * Create an {@code InboundMappingOutcomes} object from an error.
     *
     * @param message the inbound message.
     * @param error the error.
     * @param sender the actor who cares about the result of the error response.
     * @return the {@code InboundMappingOutcomes} object.
     */
    public static InboundMappingOutcomes of(final ExternalMessage message, final Exception error,
            final ActorRef sender) {
        return new InboundMappingOutcomes(List.of(), message, error, sender);
    }

    /**
     * Create an {@code InboundMappingOutcomes} object.
     *
     * @param outcomes the mapping outcomes.
     * @param externalMessage the external message.
     * @param sender actor who cares about the outcome of this message.
     * @return the {@code InboundMappingOutcomes} object.
     */
    public static InboundMappingOutcomes of(final List<MappingOutcome<MappedInboundExternalMessage>> outcomes,
            final ExternalMessage externalMessage,
            final ActorRef sender) {
        return new InboundMappingOutcomes(outcomes, externalMessage, null, sender);
    }

    /**
     * Retrieve the mapping outcomes of an inbound message.
     *
     * @return the outcomes.
     */
    public List<MappingOutcome<MappedInboundExternalMessage>> getOutcomes() {
        return outcomes;
    }

    /**
     * Retrieve the external message.
     *
     * @return the external message.
     */
    public ExternalMessage getExternalMessage() {
        return externalMessage;
    }

    /**
     * Retrieve the error, if any.
     *
     * @return the error.
     */
    @Nullable
    public Exception getError() {
        return error;
    }

    /**
     * Test if the mapping outcomes result in an error response.
     *
     * @return whether an error response exists.
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * Retrieve the actor who cares about the outcome of the incoming message.
     *
     * @return the sender actor reference.
     */
    public ActorRef getSender() {
        return sender;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "outcomes=" + outcomes +
                ", externalMessage=" + externalMessage +
                ", error=" + error +
                ", sender=" + sender +
                "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcomes, externalMessage, error, sender);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof InboundMappingOutcomes that) {
            return Objects.equals(outcomes, that.outcomes) &&
                    Objects.equals(externalMessage, that.externalMessage) &&
                    Objects.equals(error, that.error) &&
                    Objects.equals(sender, that.sender);
        } else {
            return false;
        }
    }
}
