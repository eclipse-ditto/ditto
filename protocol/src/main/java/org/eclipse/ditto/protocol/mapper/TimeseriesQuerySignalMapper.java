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
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;

/**
 * Signal mapper for {@link RetrieveTimeseries} commands.
 * <p>
 * Builds the topic path {@code <ns>/<name>/things/twin/timeseries/retrieve} from the command's
 * Thing ID and serialises the carried {@link org.eclipse.ditto.timeseries.model.TimeseriesQuery}
 * into the payload value.
 *
 * @since 4.0.0
 */
final class TimeseriesQuerySignalMapper extends AbstractSignalMapper<RetrieveTimeseries> {

    @Override
    TopicPath getTopicPath(final RetrieveTimeseries signal, final TopicPath.Channel channel) {
        // Phase 1 only honours the twin channel; the channel arg is accepted to satisfy
        // SignalMapper but always twin'd here.
        return ProtocolFactory.newTopicPathBuilder(signal.getEntityId())
                .things()
                .twin()
                .timeseries()
                .retrieve()
                .build();
    }

    @Override
    void enhancePayloadBuilder(final RetrieveTimeseries command, final PayloadBuilder payloadBuilder) {
        payloadBuilder.withValue(command.getQuery().toJson());
    }
}
