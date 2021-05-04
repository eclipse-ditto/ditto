/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * A dropped signal.
 */
@NotThreadSafe
final class Dropped implements SendingOrDropped {

    private final SendingContext sendingContext;
    private final String messageTemplate;

    /**
     * Constructs a new Dropped object.
     *
     * @param sendingContext context information for the dropped signal.
     * @param messageTemplate the message template to apply when recording the "dropped" metric / log.
     * @throws NullPointerException if {@code sendingContext} is {@code null}.
     */
    Dropped(final SendingContext sendingContext, final String messageTemplate) {
        this.sendingContext = checkNotNull(sendingContext, "sendingContext");
        this.messageTemplate = checkNotNull(messageTemplate, "messageTemplate");
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    @Override
    public Optional<CompletionStage<CommandResponse>> monitorAndAcknowledge(
            final ExceptionToAcknowledgementConverter exceptionToAcknowledgementConverter) {

        final ConnectionMonitor droppedMonitor = sendingContext.getDroppedMonitor();
        final OutboundSignal.Mapped outboundSignal = sendingContext.getMappedOutboundSignal();
        final GenericTarget genericTarget = sendingContext.getGenericTarget();
        droppedMonitor.success(outboundSignal.getSource(), messageTemplate, genericTarget.getAddress());

        return Optional.empty();
    }

}
