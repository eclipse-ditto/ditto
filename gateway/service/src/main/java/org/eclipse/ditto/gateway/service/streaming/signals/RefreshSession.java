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

package org.eclipse.ditto.gateway.service.streaming.signals;

import java.time.Instant;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;

/**
 * Simple event which signals that a websocket session should be refreshed.
 */
public class RefreshSession {

    private final String correlationId;
    private final Instant sessionTimeout;
    private final AuthorizationContext authorizationContext;

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getSessionTimeout() {return sessionTimeout; }

    public AuthorizationContext getAuthorizationContext() { return authorizationContext; }

    public RefreshSession(final String correlationId, final Instant sessionTimeout,
            final AuthorizationContext authorizationContext) {
        this.correlationId = correlationId;
        this.sessionTimeout = sessionTimeout;
        this.authorizationContext = authorizationContext;
    }
}
