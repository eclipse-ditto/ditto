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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.Objects;

import akka.actor.ActorRef;

/**
 * A message packed together with sender.
 *
 * @param <T> type of message.
 */
public final class WithSender<T> {

    private final T message;
    private final ActorRef sender;

    private WithSender(final T message, final ActorRef sender) {
        this.message = message;
        this.sender = sender;
    }

    /**
     * Create a message with sender.
     *
     * @param message the message.
     * @param sender the sender.
     * @param <T> type of message.
     * @return message and sender bundled together.
     */
    public static <T> WithSender<T> of(final T message, final ActorRef sender) {
        return new WithSender<>(message, sender);
    }

    /**
     * @return the message.
     */
    public T getMessage() {
        return message;
    }

    /**
     * @return the sender.
     */
    public ActorRef getSender() {
        return sender;
    }

    /**
     * Replace the message.
     *
     * @param newMessage the new message.
     * @param <S> type of the new message.
     * @return copy of this object with message replaced.
     */
    public <S> WithSender<S> withMessage(final S newMessage) {
        return of(newMessage, sender);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof WithSender) {
            final WithSender that = (WithSender) o;
            return Objects.equals(sender, that.sender) && Objects.equals(message, that.message);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, sender);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [message=" + message +
                ", sender=" + sender +
                "]";
    }
}
