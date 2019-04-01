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
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import java.util.function.Consumer;

/**
 * Contains {@code stoppedTimerConsumer} to be invoked when a Timer stops.
 */
public class OnStopHandler {

    private final Consumer<StoppedTimer> stoppedTimerConsumer;

    /**
     * Creates a new OnStopHandler instance.
     * @param stoppedTimerConsumer the Consumer to register.
     */
    public OnStopHandler(final Consumer<StoppedTimer> stoppedTimerConsumer) {
        this.stoppedTimerConsumer = stoppedTimerConsumer;
    }

    /**
     * Handles the passed {@code stoppedTimer} by passing it to the registered {@code stoppedTimerConsumer}.
     * @param stoppedTimer the StoppedTimer to pass along.
     */
    public void handleStoppedTimer(final StoppedTimer stoppedTimer) {
        this.stoppedTimerConsumer.accept(stoppedTimer);
    }
}
