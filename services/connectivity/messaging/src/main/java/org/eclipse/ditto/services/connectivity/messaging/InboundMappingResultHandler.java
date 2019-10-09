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

import java.util.function.Consumer;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;

/**
 * {@link MappingResultHandler} for inbound messages. This handler forwards to the given handlers. Additionally it
 * calls the {@link MappingResultHandler#onException(Exception)} method for exceptions thrown in handlers and
 * increases the according counters for mapped, dropped failed messages.
 */
public class InboundMappingResultHandler implements MappingResultHandler<MappedInboundExternalMessage> {

    private final Consumer<MappedInboundExternalMessage> onMessageMapped;
    private final Runnable onMessageDropped;
    private final Consumer<Exception> onException;
    private final ConnectionMonitor inboundMapped;
    private final ConnectionMonitor inboundDropped;
    private final ConnectionMonitor.InfoProvider infoProvider;

    InboundMappingResultHandler(
            final Consumer<MappedInboundExternalMessage> onMessageMapped, final Runnable onMessageDropped,
            final Consumer<Exception> onException,
            final ConnectionMonitor inboundMapped,
            final ConnectionMonitor inboundDropped,
            ConnectionMonitor.InfoProvider infoProvider) {
        this.onMessageMapped = onMessageMapped;
        this.onMessageDropped = onMessageDropped;
        this.onException = onException;
        this.inboundMapped = inboundMapped;
        this.inboundDropped = inboundDropped;
        this.infoProvider = infoProvider;
    }

    @Override
    public void onMessageMapped(final MappedInboundExternalMessage inboundExternalMessage) {
        try {
            inboundMapped.success(infoProvider);
            onMessageMapped.accept(inboundExternalMessage);
        } catch (final Exception e) {
            onException(e);
        }
    }

    @Override
    public void onMessageDropped() {
        try {
            inboundDropped.success(infoProvider);
            onMessageDropped.run();
        } catch (Exception e) {
            onException(e);
        }
    }

    @Override
    public void onException(final Exception exception) {
        if (exception instanceof DittoRuntimeException) {
            inboundMapped.failure(((DittoRuntimeException) exception));
        } else {
            inboundMapped.exception(exception);
        }
        onException.accept(exception);
    }

}
