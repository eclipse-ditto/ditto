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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActor.OutboundSignalWithId;

import java.util.Collection;

import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;

import akka.stream.javadsl.Source;

/**
 * {@link MappingResultHandler} for outbound messages. This handler forwards to the given handlers and
 * calls the {@link MappingResultHandler#onException(Exception)} method for exceptions thrown in these handlers and
 * increases the according counters for mapped, dropped failed messages.
 */
public class OutboundMappingResultHandler extends
        AbstractMappingResultHandler<OutboundSignal.Mapped, Source<OutboundSignalWithId, ?>> {

    private OutboundMappingResultHandler(final Builder builder) {
        super(builder);
    }

    static Builder newBuilder() {
        return new Builder().emptyResult(Source.empty()).combineResults(Source::concat);
    }

    static final class Builder extends
            AbstractBuilder<OutboundSignal.Mapped, Source<OutboundSignalWithId, ?>, Builder> {

        @Override
        protected Builder getSelf() {
            return this;
        }

        OutboundMappingResultHandler build() {
            return new OutboundMappingResultHandler(this);
        }

        Builder outboundMapped(final Collection<ConnectionMonitor> outboundMapped) {
            mappedMonitors = outboundMapped;
            return this;
        }

        Builder outboundDropped(final Collection<ConnectionMonitor> outboundDropped) {
            droppedMonitors = outboundDropped;
            return this;
        }
    }

}
