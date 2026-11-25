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

import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse;

/**
 * Signal mapper for {@link RetrieveTimeseriesResponse}.
 * <p>
 * Reuses the same {@code <ns>/<name>/things/twin/timeseries/retrieve} topic path as the inbound
 * command and serialises the per-path {@link TimeseriesQueryResult} list as a JSON array
 * payload.
 *
 * @since 4.0.0
 */
final class TimeseriesQueryResponseSignalMapper extends AbstractSignalMapper<RetrieveTimeseriesResponse> {

    @Override
    TopicPath getTopicPath(final RetrieveTimeseriesResponse signal, final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPathBuilder(signal.getEntityId())
                .things()
                .twin()
                .timeseries()
                .retrieve()
                .build();
    }

    @Override
    void enhancePayloadBuilder(final RetrieveTimeseriesResponse signal,
            final PayloadBuilder payloadBuilder) {

        final JsonArrayBuilder resultsBuilder = JsonFactory.newArrayBuilder();
        for (final TimeseriesQueryResult result : signal.getResults()) {
            resultsBuilder.add(result.toJson());
        }
        payloadBuilder.withValue(resultsBuilder.build());
        payloadBuilder.withStatus(signal.getHttpStatus());
    }
}
