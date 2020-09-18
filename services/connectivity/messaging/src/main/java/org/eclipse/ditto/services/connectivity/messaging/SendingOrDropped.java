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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.signals.acks.base.Acknowledgement;

/**
 * Either a signal being sent represented by a future acknowledgement, or a dropped signal.
 */
@NotThreadSafe
interface SendingOrDropped {

    /**
     * Returns an optional future Acknowledgement capturing the result of message sending.
     * <ul>
     * <li>If the optional is empty, then the message is dropped.</li>
     * <li>If the future has the value {@code null}, then the acknowledgement is logged and not requested.</li>
     * <li>If the future has a non-{@code null} acknowledgement, then that ack is requested and should be sent back.</li>
     * </ul>
     *
     * @return the send result optional.
     */
    Optional<CompletionStage<Acknowledgement>> monitorAndAcknowledge(
            ExceptionToAcknowledgementConverter exceptionToAcknowledgementConverter);

}
