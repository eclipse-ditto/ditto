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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.Objects;

import akka.actor.ActorRef;

/**
 * A message packed together with sender.
 *
 * @param <T> type of message.
 */
// TODO: remove after refactoring enforcements to operate on contextual messages only
public interface WithSender<T> {

    /**
     * @return the message.
     */
    T getMessage();

    /**
     * @return the sender.
     */
    ActorRef getSender();

    /**
     * Replace the message.
     *
     * @param newMessage the new message.
     * @param <S> type of the new message.
     * @return copy of this object with message replaced.
     */
    <S> WithSender<S> withMessage(final S newMessage);

    /**
     * Create a message with sender.
     *
     * @param message the message.
     * @param sender the sender.
     * @param <T> type of message.
     * @return message and sender bundled together.
     */
    static <T> WithSender<T> of(final T message, final ActorRef sender) {
        return new WithSenderImpl<>(message, sender);
    }

    final class WithSenderImpl<T> implements WithSender<T> {

        private final T message;
        private final ActorRef sender;

        private WithSenderImpl(final T message, final ActorRef sender) {
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
        public <S> WithSender<S> withMessage(final S newMessage) {
            return of(newMessage, sender);
        }

        @Override
        public boolean equals(final Object o) {
            if (o instanceof WithSenderImpl) {
                final WithSenderImpl that = (WithSenderImpl) o;
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
}
