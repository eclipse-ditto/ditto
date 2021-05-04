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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

import akka.actor.ActorRef;

/**
 * A message packed together with sender.
 *
 * @param <T> type of message.
 */
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
     * @return copy of this object with message replaced.
     */
    WithSender<T> withMessage(T newMessage);

    /**
     * Create a message with sender.
     *
     * @param message the message.
     * @param sender the sender.
     * @param <T> type of message.
     * @return message and sender bundled together.
     */
    static <T extends WithDittoHeaders> WithSender<T> of(final T message, final ActorRef sender) {
        return ControlFlowFactory.messageWithSender(message, sender);
    }

}
