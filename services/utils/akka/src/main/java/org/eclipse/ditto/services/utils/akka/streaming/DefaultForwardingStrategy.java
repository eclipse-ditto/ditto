/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_FINISHED_MSG;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;

/**
 * Default implementation of {@link ForwardingStrategy}.
 *
 * @param <E> Type of received stream elements of the forwarder.
 */
public class DefaultForwardingStrategy<E> implements ForwardingStrategy<E> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultForwardingStrategy.class);

    private final ActorRef recipient;
    private final Function<E, String> elementIdentifierFunction;
    private final ActorRef completionRecipient;

    /**
     * Constructor.
     *
     * @param recipient the actor to send transformed stream elements to.
     * @param elementIdentifierFunction a function which maps a stream element to an identifier (to correlate acks).
     * @param completionRecipient recipient of the message sent when a stream has been completed.
     */
    public DefaultForwardingStrategy(final ActorRef recipient,
            final Function<E, String> elementIdentifierFunction,
            final ActorRef completionRecipient) {
        this.recipient = requireNonNull(recipient);
        this.elementIdentifierFunction = requireNonNull(elementIdentifierFunction);
        this.completionRecipient = requireNonNull(completionRecipient);
    }

    @Override
    public void forward(final E element, final ActorRef forwarderActorRef, final ForwarderCallback callback) {
        final String identifier = elementIdentifierFunction.apply(element);
        LOGGER.debug("Computed element identifier: {}", identifier);

        // important: callback before dispatching the answer, otherwise the message might not be acknowledged
        callback.forwarded(identifier);

        recipient.tell(element, forwarderActorRef);
    }

    @Override
    public void onComplete(final ActorRef forwarderActorRef) {
        completionRecipient.tell(STREAM_FINISHED_MSG, forwarderActorRef);
    }

    @Override
    public void maxIdleTimeExceeded(final ActorRef forwarderActorRef) {
        completionRecipient.tell(FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG, forwarderActorRef);
    }
}
