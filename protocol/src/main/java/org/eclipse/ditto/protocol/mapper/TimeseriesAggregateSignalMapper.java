/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseries;

/**
 * Signal mapper for {@link RetrieveAggregatedTimeseries} commands.
 * <p>
 * Unlike {@link TimeseriesQuerySignalMapper}, this command targets no single Thing — it aggregates
 * across a whole namespace and is deliberately not a {@code WithEntityId} signal. It borrows the
 * {@code _} entity-name placeholder that {@code CheckPermissions} uses for its cross-entity command,
 * but keeps the real namespace in the namespace position:
 * {@code <namespace>/_/things/twin/timeseries/aggregate}.
 * <p>
 * Putting the namespace in the topic rather than only in the payload keeps it visible to
 * topic-level routing and filtering, and lets the response be reconstructed without an envelope —
 * the response entity carries {@code results} and {@code authorization} but not the namespace.
 *
 * @since 4.0.0
 */
final class TimeseriesAggregateSignalMapper extends AbstractSignalMapper<RetrieveAggregatedTimeseries> {

    @Override
    TopicPath getTopicPath(final RetrieveAggregatedTimeseries signal, final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPath(
                TimeseriesAggregateTopicPath.forNamespace(signal.getNamespace()));
    }

    @Override
    void enhancePayloadBuilder(final RetrieveAggregatedTimeseries command,
            final PayloadBuilder payloadBuilder) {

        payloadBuilder.withValue(command.getQuery().toJson());
    }
}
