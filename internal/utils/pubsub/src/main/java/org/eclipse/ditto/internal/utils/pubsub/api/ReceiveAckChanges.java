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
package org.eclipse.ditto.internal.utils.pubsub.api;

import akka.actor.ActorRef;

/**
 * Abstract super class of change notification requests for {@code AckUpdater}.
 */
abstract class ReceiveAckChanges implements AckRequest {

    private final ActorRef receiver;

    ReceiveAckChanges(final ActorRef receiver) {
        this.receiver = receiver;
    }

    /**
     * Retrieve the receiver of changes.
     *
     * @return the receiver.
     */
    public ActorRef getReceiver() {
        return receiver;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[receiver=" + receiver + "]";
    }
}
