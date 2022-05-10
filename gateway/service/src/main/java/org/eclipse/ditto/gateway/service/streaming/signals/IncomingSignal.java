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
package org.eclipse.ditto.gateway.service.streaming.signals;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Envelope of a signal to mark it as incoming for {@code StreamingSessionActor}.
 */
@Immutable
public final class IncomingSignal {

    private final Signal<?> signal;

    private IncomingSignal(final Signal<?> signal) {
        this.signal = signal;
    }

    /**
     * Wrap a signal and mark it as incoming.
     *
     * @param signal the signal.
     * @return the wrapped signal.
     */
    public static IncomingSignal of(final Signal<?> signal) {
        return new IncomingSignal(signal);
    }

    /**
     * Extract the signal.
     *
     * @return the signal.
     */
    public Signal<?> getSignal() {
        return signal;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IncomingSignal that = (IncomingSignal) o;
        return Objects.equals(signal, that.signal);
    }

    @Override
    public int hashCode() {
        return signal.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "signal=" + signal +
                "]";
    }
}
