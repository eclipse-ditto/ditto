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
package org.eclipse.ditto.connectivity.service.messaging.persistence.strategies.commands;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogsResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogs} command.
 */
final class EnableConnectionLogsStrategy extends AbstractEphemeralStrategy<EnableConnectionLogs> {

    EnableConnectionLogsStrategy() {
        super(EnableConnectionLogs.class);
    }

    @Override
    WithDittoHeaders getResponse(final ConnectionState state, final DittoHeaders headers) {
        return EnableConnectionLogsResponse.of(state.id(), headers);
    }

    @Override
    List<ConnectionAction> getActions() {
        return Arrays.asList(ConnectionAction.BROADCAST_TO_CLIENT_ACTORS_IF_STARTED, ConnectionAction.SEND_RESPONSE, ConnectionAction.ENABLE_LOGGING);
    }
}
