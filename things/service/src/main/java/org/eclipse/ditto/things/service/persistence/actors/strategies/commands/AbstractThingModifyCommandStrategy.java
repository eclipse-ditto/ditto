/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

/**
 * Abstract base class for {@link ThingModifyCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code ThingModifyCommand}
 */
@Immutable
abstract class AbstractThingModifyCommandStrategy<C extends ThingModifyCommand<C>>
        extends AbstractThingCommandStrategy<C> {

    protected AbstractThingModifyCommandStrategy(final Class<C> theMatchingClass, final ActorSystem actorSystem) {
        super(theMatchingClass, actorSystem);
    }

    /**
     * Builds a CompletionStage which asynchronously validates the passed in {@code command} and the {@code thing},
     * using the {@link #performWotValidation(ThingModifyCommand, Thing)} abstract method of this class.
     *
     * @param command the command to validate
     * @param thing the (previous) thing state to use for obtaining validation information
     * @return a CompletionStage which asynchronously validates the passed in {@code command} and fails with a
     * {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException} if the command could not be
     * validated successfully
     */
    protected CompletionStage<C> buildValidatedStage(final C command, @Nullable final Thing thing) {
        final var startedSpan = DittoTracing.newPreparedSpan(
                        command.getDittoHeaders(),
                        SpanOperationName.of("enforce_wot_model")
                )
                .correlationId(command.getDittoHeaders().getCorrelationId().orElse(null))
                .start();
        final var tracedCommand =
                command.setDittoHeaders(DittoHeaders.of(startedSpan.propagateContext(command.getDittoHeaders())));
        return performWotValidation(tracedCommand, thing)
                .whenComplete((result, throwable) -> {
                    if (throwable instanceof CompletionException completionException) {
                        if (completionException.getCause() instanceof DittoRuntimeException dre) {
                            startedSpan.tagAsFailed(dre.toString())
                                    .finish();
                        } else {
                            startedSpan.tagAsFailed(
                                    completionException.getCause().getClass().getSimpleName() + ": " +
                                            throwable.getCause().getMessage()
                                    ).finish();
                        }
                        return;
                    }

                    if (throwable != null) {
                        startedSpan.tagAsFailed(throwable.getClass().getSimpleName() + ": " + throwable.getMessage())
                                .finish();
                    } else {
                        startedSpan.finish();
                    }
                });

    }

    /**
     * Performs WoT based validation of the passed {@code command} and {@code thing}, depending on the concrete command
     * strategy.
     *
     * @param command the command to validate
     * @param thing the thing to validate
     * @return a CompletionStage which asynchronously validates the passed in {@code command} and fails with a
     * {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException} if the command could not be
     * validated successfully
     */
    protected abstract CompletionStage<C> performWotValidation(C command, @Nullable Thing thing);
}
