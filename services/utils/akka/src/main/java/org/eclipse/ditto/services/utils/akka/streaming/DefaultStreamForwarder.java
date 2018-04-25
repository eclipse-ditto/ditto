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

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Source;

/**
 * This actor is responsible for forwarding streamed entities to a configurable recipient and signalling completion to
 * another configurable recipient.
 *
 * @param <E> Type of received stream elements.
 */
public final class DefaultStreamForwarder<E> extends AbstractStreamForwarder<E> {

    private final ActorRef recipient;
    private final ActorRef completionRecipient;
    private final Duration maxIdleTime;
    private final Class<E> elementClass;
    private final Function<E, Source<Object, NotUsed>> mapEntityFunction;

    private DefaultStreamForwarder(final ActorRef recipient, final ActorRef completionRecipient,
            final Duration maxIdleTime, final Class<E> elementClass,
            final Function<E, Source<Object, NotUsed>> mapEntityFunction) {
        this.recipient = requireNonNull(recipient);
        this.completionRecipient = requireNonNull(completionRecipient);
        this.maxIdleTime = requireNonNull(maxIdleTime);
        this.elementClass = requireNonNull(elementClass);
        this.mapEntityFunction = requireNonNull(mapEntityFunction);
    }

    /**
     * Creates a {@code Props} object to instantiate this actor.
     *
     * @param recipient Actor reference which is the recipient of the streamed messages.
     * @param completionRecipient recipient of the message sent when a stream has been completed.
     * @param maxIdleTime Maximum time this actor stays alive without receiving any message.
     * @param elementClass the class of stream elements.
     * @param mapEntity the function to transform elements into a source of messages to forward.
     * @param <E> Type of received stream elements.
     * @return The {@code Props} object.
     */
    public static <E> Props props(final ActorRef recipient, final ActorRef completionRecipient,
            final Duration maxIdleTime, final Class<E> elementClass,
            final Function<E, Source<Object, NotUsed>> mapEntity) {
        return Props.create(DefaultStreamForwarder.class, () ->
                new DefaultStreamForwarder<>(recipient, completionRecipient, maxIdleTime, elementClass, mapEntity));
    }

    @Override
    protected Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    protected Class<E> getElementClass() {
        return elementClass;
    }

    @Override
    protected ActorRef getRecipient() {
        return recipient;
    }

    @Override
    protected ActorRef getCompletionRecipient() {
        return completionRecipient;
    }

    @Override
    protected Source<Object, NotUsed> mapEntity(final E element) {
        return mapEntityFunction.apply(element);
    }

}
