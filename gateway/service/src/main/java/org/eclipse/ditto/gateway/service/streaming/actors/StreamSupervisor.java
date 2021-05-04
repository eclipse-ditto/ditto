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
package org.eclipse.ditto.gateway.service.streaming.actors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Provides the means to supervise a materialized stream.
 */
public interface StreamSupervisor {

    /**
     * Supervises the given WebSocket actor.
     *
     * @param supervisedStream component in a materialized stream to be supervised.
     * @param correlationId the correlation ID identifying the stream.
     * @param dittoHeaders provide information which may be useful for supervision.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code connectionCorrelationId} is empty.
     */
    void supervise(SupervisedStream supervisedStream, CharSequence correlationId, DittoHeaders dittoHeaders);

}
