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

public class OnStopHandler {

    private final Consumer<StoppedTimer> stoppedTimerConsumer;

    public OnStopHandler(final Consumer<StoppedTimer> stoppedTimerConsumer) {
        this.stoppedTimerConsumer = stoppedTimerConsumer;
    }

    public void handleStoppedTimer(final StoppedTimer stoppedTimer) {
        this.stoppedTimerConsumer.accept(stoppedTimer);
    }
}
