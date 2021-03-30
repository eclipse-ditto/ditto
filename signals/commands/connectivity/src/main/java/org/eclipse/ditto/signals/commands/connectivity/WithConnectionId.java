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
package org.eclipse.ditto.signals.commands.connectivity;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.signals.base.WithEntityId;

/**
 * Represents a signal type that is able to return its ConnectionId. Provides a default implementation for
 * {@link #getEntityId()} which returns the ConnectionId as well.
 */
public interface WithConnectionId extends WithEntityId {

    /**
     * Returns the identifier of the Connection.
     *
     * @return the identifier of the Connection.
     */
    ConnectionId getConnectionEntityId();

    @Override
    default ConnectionId getEntityId() {
        return getConnectionEntityId();
    }
}
