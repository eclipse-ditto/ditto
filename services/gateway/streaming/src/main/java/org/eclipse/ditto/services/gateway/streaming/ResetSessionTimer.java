/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.gateway.streaming;

import java.time.Instant;

public class ResetSessionTimer {

    private final String correlationId;
    private final Instant sessionTimeout;

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getSessionTimeout() {return sessionTimeout; }

    public ResetSessionTimer(final String correlationId, final Instant sessionTimeout) {
        this.correlationId = correlationId;
        this.sessionTimeout = sessionTimeout;
    }
}
