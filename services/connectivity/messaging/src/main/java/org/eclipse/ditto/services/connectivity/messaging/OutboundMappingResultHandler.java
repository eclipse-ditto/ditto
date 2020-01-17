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

import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;

/**
 * {@link MappingResultHandler} for outbound messages. This handler forwards to the given handlers and
 * calls the {@link MappingResultHandler#onException(Exception)} method for exceptions thrown in these handlers and
 * increases the according counters for mapped, dropped failed messages.
 */
public class OutboundMappingResultHandler implements MappingResultHandler<OutboundSignal.Mapped, Void> {

    private final Consumer<OutboundSignal.Mapped> onMessageMapped;
    private final Runnable onMessageDropped;
    private final Consumer<Exception> onException;
    private final Set<ConnectionMonitor> outboundMapped;
    private final Set<ConnectionMonitor> outboundDropped;
    private final ConnectionMonitor.InfoProvider infoProvider;

    OutboundMappingResultHandler(final Consumer<OutboundSignal.Mapped> onMessageMapped, final Runnable onMessageDropped,
            final Consumer<Exception> onException, final Set<ConnectionMonitor> outboundMapped,
            final Set<ConnectionMonitor> outboundDropped, final ConnectionMonitor.InfoProvider infoProvider) {
        this.onMessageMapped = onMessageMapped;
        this.onMessageDropped = onMessageDropped;
        this.onException = onException;
        this.outboundMapped = outboundMapped;
        this.outboundDropped = outboundDropped;
        this.infoProvider = infoProvider;
    }

    @Override
    public Void onMessageMapped(final OutboundSignal.Mapped outboundMappedMessage) {
        try {
            outboundMapped.forEach(monitor -> monitor.success(infoProvider));
            onMessageMapped.accept(outboundMappedMessage);
            return null;
        } catch (final Exception e) {
            return onException(e);
        }
    }

    @Override
    public Void onMessageDropped() {
        try {
            outboundDropped.forEach(monitor -> monitor.success(infoProvider));
            onMessageDropped.run();
            return null;
        } catch (Exception e) {
            return onException(e);
        }
    }

    @Override
    public Void onException(final Exception exception) {
        if (exception instanceof DittoRuntimeException) {
            outboundMapped.forEach(monitor -> monitor.failure(((DittoRuntimeException) exception)));
        } else {
            outboundMapped.forEach(monitor -> monitor.exception(exception));
        }
        onException.accept(exception);
        return null;
    }

    @Override
    public Void combineResults(final Void left, final Void right) {
        // TODO
        return null;
    }

    @Override
    public Void emptyResult() {
        // TODO
        return null;
    }

}
