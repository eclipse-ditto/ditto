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
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;

/**
 * This strategy handles the {@link ResetConnectionMetrics}
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
        return Arrays.asList(ConnectionAction.BROADCAST_TO_CLIENT_ACTORS_IF_STARTED, ConnectionAction.SEND_RESPONSE);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ResetConnectionMetrics command,
            @Nullable final Connection previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ResetConnectionMetrics command,
            @Nullable final Connection newEntity) {
        return Optional.empty();
    }
}
