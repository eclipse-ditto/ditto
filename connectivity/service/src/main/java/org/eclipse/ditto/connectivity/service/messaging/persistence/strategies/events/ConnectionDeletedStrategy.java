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
package org.eclipse.ditto.connectivity.service.messaging.persistence.strategies.events;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionLifecycle;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;

/**
 * This strategy handles the {@link ConnectionDeleted} event.
 */
final class ConnectionDeletedStrategy implements EventStrategy<ConnectionDeleted, Connection> {

    @Nullable
    @Override
    public Connection handle(final ConnectionDeleted event, @Nullable final Connection connection,
            final long revision) {
        if (connection != null) {
            return connection.toBuilder().lifecycle(ConnectionLifecycle.DELETED)
                    .modified(event.getTimestamp().orElse(null))
                    .build();
        } else {
            return null;
        }
    }
}
