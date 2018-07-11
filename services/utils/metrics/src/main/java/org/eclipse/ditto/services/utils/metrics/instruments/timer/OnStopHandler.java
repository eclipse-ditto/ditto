/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
