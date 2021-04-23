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

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Either a signal being sent represented by a future acknowledgement, or a dropped signal.
 */
@NotThreadSafe
interface SendingOrDropped {

    /**
     * Returns an optional future command response or acknowledgement capturing the result of message sending.
     * <ul>
     * <li>If the optional is empty, then the message is dropped.</li>
     * <li>If the future has the value {@code null}, then the response is logged and not requested.</li>
     * <li>If the future has a non-{@code null} result, then that ack is requested and should be sent back.</li>
     * </ul>
     *
     * @return the send result optional.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    Optional<CompletionStage<CommandResponse>> monitorAndAcknowledge(
            ExceptionToAcknowledgementConverter exceptionToAcknowledgementConverter);

}
