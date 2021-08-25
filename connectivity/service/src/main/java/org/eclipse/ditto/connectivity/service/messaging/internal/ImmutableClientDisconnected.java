/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import javax.annotation.concurrent.Immutable;

import akka.actor.ActorRef;

/**
 * Immutable implementation of {@link ClientDisconnected}.
 */
@Immutable
final class ImmutableClientDisconnected extends AbstractWithOrigin implements ClientDisconnected {

    private final boolean shutdownAfterDisconnected;

    ImmutableClientDisconnected(@Nullable final ActorRef origin, final boolean shutdownAfterDisconnected) {
        super(origin);
        this.shutdownAfterDisconnected = shutdownAfterDisconnected;
    }

    @Override
    public boolean shutdownAfterDisconnected() {
        return shutdownAfterDisconnected;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", shutdownAfterDisconnected=" + shutdownAfterDisconnected +
                "]";
    }
}
