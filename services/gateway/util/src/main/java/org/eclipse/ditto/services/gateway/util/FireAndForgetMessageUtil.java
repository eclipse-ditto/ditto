/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.util;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;

/**
 * Implements fire-and-forget semantics for message commands. Message commands sent with timeout 0 are considered
 * fire-and-forget.
 */
public final class FireAndForgetMessageUtil {

    private FireAndForgetMessageUtil() {}

    /**
     * Creates an @{SendMessageAcceptedResponse} for a message command if it is fire-and-forget.
     *
     * @param command The message command.
     * @return The HTTP response if the message command is fire-and-forget, {@code Optional.empty()} otherwise.
     */
    public static Optional<SendMessageAcceptedResponse> getResponseForFireAndForgetMessage(
            final MessageCommand<?, ?> command) {
        if (isFireAndForgetMessage(command)) {
            return Optional.of(
                    SendMessageAcceptedResponse.newInstance(command.getThingId(), command.getMessage().getHeaders(),
                            command.getDittoHeaders()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Tests whether a message command is fire-and-forget.
     *
     * @param command The message command.
     * @return {@code true} if the message's timeout header is 0 or if the message is flagged not to require a response,
     * {@code false} otherwise.
     */
    public static boolean isFireAndForgetMessage(final MessageCommand<?, ?> command) {
        return command.getMessage()
                .getTimeout()
                .map(Duration::isZero)
                .orElseGet(() -> !command.getDittoHeaders().isResponseRequired());
    }
}
