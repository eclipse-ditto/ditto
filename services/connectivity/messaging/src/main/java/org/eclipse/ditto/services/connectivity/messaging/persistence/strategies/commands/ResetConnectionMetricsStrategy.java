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
package org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.commands;

import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.TELL_CLIENT_ACTORS_IF_STARTED;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetricsResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics}
 * command.
 */
final class ResetConnectionMetricsStrategy extends AbstractEphemeralStrategy<ResetConnectionMetrics> {

    ResetConnectionMetricsStrategy() {
        super(ResetConnectionMetrics.class);
    }

    @Override
    WithDittoHeaders getResponse(final ConnectionState state, final DittoHeaders headers) {
        return ResetConnectionMetricsResponse.of(state.id(), headers);
    }

    @Override
    List<ConnectionAction> getActions() {
        return Arrays.asList(TELL_CLIENT_ACTORS_IF_STARTED, SEND_RESPONSE);
    }
}
