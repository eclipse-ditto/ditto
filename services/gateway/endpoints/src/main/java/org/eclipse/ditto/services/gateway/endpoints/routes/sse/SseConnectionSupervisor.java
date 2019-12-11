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
package org.eclipse.ditto.services.gateway.endpoints.routes.sse;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

import akka.actor.ActorRef;

/**
 * Provides the means to supervise a particular SSE connection actor.
 */
public interface SseConnectionSupervisor {

    /**
     * Supervises the given SSE connection actor.
     *
     * @param sseConnectionActor the SSE connection actor to be supervised.
     * @param connectionCorrelationId the correlation ID of the SSE connection to be supervised.
     * @param dittoHeaders provide information which may be useful for supervision.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code connectionCorrelationId} is empty.
     */
    void supervise(ActorRef sseConnectionActor, CharSequence connectionCorrelationId, DittoHeaders dittoHeaders);

}
