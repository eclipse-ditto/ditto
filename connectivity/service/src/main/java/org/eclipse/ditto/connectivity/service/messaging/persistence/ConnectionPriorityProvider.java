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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.connectivity.model.ConnectionId;

/**
 * Provides the priority of a given connection.
 */
@FunctionalInterface
public interface ConnectionPriorityProvider {

    /**
     * Provides the priority of a given connection.
     *
     * @param connectionId the ID of the connection for which the priority should be returned.
     * @param correlationId the correlation ID.
     * @return the priority.
     */
    CompletionStage<Integer> getPriorityFor(ConnectionId connectionId, String correlationId);

}
