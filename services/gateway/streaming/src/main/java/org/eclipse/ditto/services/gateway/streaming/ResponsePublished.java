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
package org.eclipse.ditto.services.gateway.streaming;

/**
 * Simple event which signals that a response (regular response or error) was published.
 */
public class ResponsePublished {

    private final String correlationId;

    public String getCorrelationId() {
        return correlationId;
    }

    public ResponsePublished(final String correlationId) {
        this.correlationId = correlationId;
    }
}
