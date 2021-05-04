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
 * Factory for building "controlflow" objects.
 */
final class ControlFlowFactory {

    private ControlFlowFactory() {
        throw new AssertionError();
    }

    /**
     * Create a message with sender.
     *
     * @param message the message.
     * @param sender the sender.
     * @param <T> type of message.
     * @return message and sender bundled together.
     */
    static <T extends WithDittoHeaders> WithSender<T> messageWithSender(final T message, final ActorRef sender) {
        return new ImmutableWithSender<>(message, sender);
    }
}
