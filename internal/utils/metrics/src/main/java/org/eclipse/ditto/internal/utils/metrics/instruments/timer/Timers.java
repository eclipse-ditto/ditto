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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

/**
 * Factory methods for timers.
 */
public final class Timers {

    private Timers() {
        // No-Op because this is a factory class.
    }

    /**
     * Builds a {@link PreparedTimer} with the given name.
     */
    public static PreparedTimer newTimer(final String name) {
        return PreparedKamonTimer.newTimer(name);
    }

}
