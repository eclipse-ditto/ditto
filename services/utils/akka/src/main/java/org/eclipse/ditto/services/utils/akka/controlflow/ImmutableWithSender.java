/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

import akka.actor.ActorRef;

/**
 * Immutable implementation of {@link WithSender}.
 *
 * @param <T> type of message.
 */
@Immutable
final class ImmutableWithSender<T extends WithDittoHeaders> implements WithSender<T> {

    private final T message;
    private final ActorRef sender;

    ImmutableWithSender(final T message, final ActorRef sender) {
        this.message = message;
        this.sender = sender;
    }

    @Override
    public T getMessage() {
        return message;
    }

    @Override
    public ActorRef getSender() {
        return sender;
    }

    @Override
    public <S extends WithDittoHeaders> WithSender<S> withMessage(final S newMessage) {
        return new ImmutableWithSender<>(newMessage, sender);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof ImmutableWithSender) {
            final ImmutableWithSender that = (ImmutableWithSender) o;
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
