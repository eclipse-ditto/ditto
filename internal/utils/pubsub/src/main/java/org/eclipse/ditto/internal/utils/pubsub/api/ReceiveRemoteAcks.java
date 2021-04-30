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
 * Request to receive remote acknowledgement label declaration changes.
 */
public final class ReceiveRemoteAcks extends ReceiveAckChanges {

    private ReceiveRemoteAcks(final ActorRef receiver) {
        super(receiver);
    }

    /**
     * Create a request to receive remote acknowledgement label declaration changes.
     *
     * @param receiver who is receiving the changes.
     * @return the request.
     */
    public static AckRequest of(final ActorRef receiver) {
        return new ReceiveRemoteAcks(receiver);
    }
}
