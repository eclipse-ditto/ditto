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

import java.time.Duration;
import java.util.function.Function;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * This actor is responsible for forwarding streamed entities to a configurable recipient and signalling completion to
 * another configurable recipient.
 *
 * @param <E> Type of received stream elements.
 */
public final class DefaultStreamForwarder<E> extends AbstractStreamForwarder<E> {

    private final ForwardingStrategy<E> forwardingStrategy;
    private final Duration maxIdleTime;
    private final Class<E> elementClass;

    private DefaultStreamForwarder(final ForwardingStrategy<E> forwardingStrategy,
            final Duration maxIdleTime, final Class<E> elementClass) {
        this.forwardingStrategy = requireNonNull(forwardingStrategy);
        this.maxIdleTime = requireNonNull(maxIdleTime);
        this.elementClass = requireNonNull(elementClass);
    }

    /**
     * Creates a {@code Props} object to instantiate this actor using {@link DefaultForwardingStrategy}.
     *
     * @param recipient Actor reference which is the recipient of the streamed messages.
     * @param completionRecipient recipient of the message sent when a stream has been completed.
     * @param maxIdleTime Maximum time this actor stays alive without receiving any message.
     * @param elementClass the class of stream elements.
     * @param elementIdentifierFunction a function which maps a stream element to an identifier (to correlate acks).
     * @param <E> Type of received stream elements.
     * @return The {@code Props} object.
     */
    public static <E> Props props(final ActorRef recipient, final ActorRef completionRecipient,
            final Duration maxIdleTime, final Class<E> elementClass,
            final Function<E, String> elementIdentifierFunction) {
        return Props.create(DefaultStreamForwarder.class,
                () -> new DefaultStreamForwarder<>(new DefaultForwardingStrategy<>(recipient,
                        elementIdentifierFunction, completionRecipient),
                        maxIdleTime, elementClass));
    }

    /**
     * Creates a {@code Props} object to instantiate this actor.
     *
     * @param forwardingStrategy the strategy used for forwarding elements.
     * @param maxIdleTime Maximum time this actor stays alive without receiving any message.
     * @param elementClass the class of stream elements.
     * @param <E> Type of received stream elements.
     * @return The {@code Props} object.
     */
    public static <E> Props props(ForwardingStrategy<E> forwardingStrategy,
            final Duration maxIdleTime, final Class<E> elementClass) {
        return Props.create(DefaultStreamForwarder.class,
                () -> new DefaultStreamForwarder<>(forwardingStrategy, maxIdleTime, elementClass));
    }

    @Override
    protected ForwardingStrategy<E> getForwardingStrategy() {
        return forwardingStrategy;
    }

    @Override
    protected Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    protected Class<E> getElementClass() {
        return elementClass;
    }

}
