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
package org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.events;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionLifecycle;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted} event.
 */
final class ConnectionDeletedStrategy implements EventStrategy<ConnectionDeleted, Connection> {

    @Nullable
    @Override
    public Connection handle(final ConnectionDeleted event, @Nullable final Connection connection,
            final long revision) {
        if (connection != null) {
            return connection.toBuilder().lifecycle(ConnectionLifecycle.DELETED).build();
        } else {
            return null;
        }
    }
}
