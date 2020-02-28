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
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;

/**
 * Create processing units of Akka stream graph before enforcement from an asynchronous function that may abort
 * enforcement by throwing exceptions.
 */
@FunctionalInterface
public interface PreEnforcer {

    /**
     * Logger of pre-enforcers.
     */
    Logger LOGGER = LoggerFactory.getLogger(PreEnforcer.class);

    /**
     * Apply pre-enforcement.
     * Post-condition: the type of signal in the future of the return value is identical to the type of
     * signal in the argument.
     *
     * @param signal the signal.
     * @return future result of the pre-enforcement.
     */
    CompletionStage<WithDittoHeaders> apply(WithDittoHeaders signal);

    /**
     * Perform pre-enforcement with error handling.
     *
     * @param withSender input signal together with its sender.
     * @param onError result after pre-enforcement failure.
     * @param andThen what happens after pre-enforcement success.
     * @param <S> argument type of {@code andThen}.
     * @param <T> future value type returned by {@code andThen}.
     * @return result of the pre-enforcement.
     */
    @SuppressWarnings("unchecked") // due to cast to (S)
    default <S extends WithSender<?>, T> CompletionStage<T> withErrorHandlingAsync(final S withSender,
            @Nullable final T onError,
            final Function<S, CompletionStage<T>> andThen) {
        return apply(withSender.getMessage())
                // the cast to (S) is safe if the post-condition of this.apply(WithDittoHeaders) holds.
                .thenCompose(message -> andThen.apply((S) withSender.withMessage(message)))
                .exceptionally(error -> {
                    final ActorRef sender = withSender.getSender();
                    final DittoHeaders dittoHeaders = ((WithSender) withSender).getMessage().getDittoHeaders();
                    final DittoRuntimeException dittoRuntimeException =
                            DittoRuntimeException.asDittoRuntimeException(error, cause -> {
                                LOGGER.error("Unexpected non-DittoRuntimeException error - responding with " +
                                                "GatewayInternalErrorException: {} - {} - {}",
                                        error.getClass().getSimpleName(),
                                        error.getMessage(),
                                        error);

                                return GatewayInternalErrorException.newBuilder()
                                        .dittoHeaders(dittoHeaders)
                                        .cause(cause)
                                        .build();
                            });
                    sender.tell(dittoRuntimeException, ActorRef.noSender());
                    return onError;
                });
    }
}
