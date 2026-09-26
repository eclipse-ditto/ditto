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

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseriesResponse;

/**
 * Signal mapper for {@link RetrieveAggregatedTimeseriesResponse}.
 * <p>
 * Reuses the command's {@code <namespace>/_/things/twin/timeseries/aggregate} topic path — the
 * response has no single Thing to name either — and serialises the whole response entity, so the
 * {@code authorization} block travels with the results rather than being dropped on non-HTTP
 * transports. The namespace is recovered from the topic, since the entity does not carry it.
 *
 * @since 4.0.0
 */
final class TimeseriesAggregateResponseSignalMapper
        extends AbstractSignalMapper<RetrieveAggregatedTimeseriesResponse> {

    @Override
    TopicPath getTopicPath(final RetrieveAggregatedTimeseriesResponse signal,
            final TopicPath.Channel channel) {

        return ProtocolFactory.newTopicPath(
                TimeseriesAggregateTopicPath.forNamespace(signal.getNamespace()));
    }

    @Override
    void enhancePayloadBuilder(final RetrieveAggregatedTimeseriesResponse signal,
            final PayloadBuilder payloadBuilder) {

        payloadBuilder.withValue(signal.getEntity(JsonSchemaVersion.LATEST));
        payloadBuilder.withStatus(signal.getHttpStatus());
    }
}
