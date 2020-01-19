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

import java.util.Collections;

import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.signals.base.Signal;

import akka.stream.javadsl.Source;

/**
 * {@link MappingResultHandler} for inbound messages. This handler forwards to the given handlers. Additionally it
 * calls the {@link MappingResultHandler#onException(Exception)} method for exceptions thrown in handlers and
 * increases the according counters for mapped, dropped failed messages.
 */
final class InboundMappingResultHandler
        extends AbstractMappingResultHandler<MappedInboundExternalMessage, Source<Signal<?>, ?>> {

    private InboundMappingResultHandler(final Builder builder) {
        super(builder);
    }

    static Builder newBuilder() {
        return new Builder().emptyResult(Source.empty()).combineResults(Source::concat);
    }

    static final class Builder extends AbstractBuilder<MappedInboundExternalMessage, Source<Signal<?>, ?>, Builder> {

        private Builder() {}

        @Override
        protected Builder getSelf() {
            return this;
        }

        InboundMappingResultHandler build() {
            return new InboundMappingResultHandler(this);
        }

        Builder inboundMapped(final ConnectionMonitor inboundMapped) {
            mappedMonitors = Collections.singletonList(inboundMapped);
            return this;
        }

        Builder inboundDropped(final ConnectionMonitor inboundDropped) {
            droppedMonitors = Collections.singletonList(inboundDropped);
            return this;
        }

    }

}
