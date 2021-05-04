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
package org.eclipse.ditto.internal.utils.persistence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * A wrapper of the result of {@link akka.japi.pf.ReceiveBuilder} that never fails.
 */
public final class SafeRecovery {

    /**
     * Creates a Receive for recovery such that exceptions are logged as warnings
     * and not thrown. Recovery messages causing exceptions have no effect.
     *
     * @param log The Akka logger to write warnings to.
     * @param receiveRecover The Receive to wrap around.
     *
     * @return the created Receive.
     */
    public static AbstractActor.Receive wrapReceive(
            @Nullable final DiagnosticLoggingAdapter log,
            @Nonnull final AbstractActor.Receive receiveRecover) {
        return ReceiveBuilder.create().matchAny(x -> {
            try {
                receiveRecover.onMessage().apply(x);
            } catch (final Exception error) {
                if (log != null) {
                    log.warning("Failed to recover from the following message (it is ignored): {}", x);
                }
            }
        }).build();
    }

    private SafeRecovery() {
        // no-op
    }
}
