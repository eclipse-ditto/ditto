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
package org.eclipse.ditto.connectivity.service.messaging.internal;

import javax.annotation.Nullable;

import akka.actor.ActorRef;

/**
 * Messaging internal event when a
 * {@link org.eclipse.ditto.connectivity.service.messaging.BaseClientActor Client} disconnected.
 */
public interface ClientDisconnected extends WithOrigin {

    /**
     * Creates a new ConnectionFailure of not yet known differentiation between
     * {@link org.eclipse.ditto.connectivity.model.ConnectivityStatus#FAILED} and {@link org.eclipse.ditto.connectivity.model.ConnectivityStatus#MISCONFIGURED}.
     *
     * @param origin the origin ActorRef
     * @param shutdownAfterDisconnected the cause of the Failure
     * @return the created ClientDisconnected
     */
    static ClientDisconnected of(@Nullable final ActorRef origin, final boolean shutdownAfterDisconnected) {
        return new ImmutableClientDisconnected(origin, shutdownAfterDisconnected);
    }

    /**
     * @return whether the client actor should be closed after disconnecting or not.
     */
    boolean shutdownAfterDisconnected();
}
