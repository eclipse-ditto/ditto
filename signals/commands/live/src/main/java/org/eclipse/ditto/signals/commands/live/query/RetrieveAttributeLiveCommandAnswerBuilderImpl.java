/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.live.query;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.live.query.RetrieveAttributeLiveCommandAnswerBuilder.ResponseFactory;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * RetrieveAttributeLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveAttributeLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<RetrieveAttributeLiveCommand, ResponseFactory>
        implements RetrieveAttributeLiveCommandAnswerBuilder {

    private RetrieveAttributeLiveCommandAnswerBuilderImpl(final RetrieveAttributeLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveAttributeLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveAttributeLiveCommandAnswerBuilderImpl newInstance(
            final RetrieveAttributeLiveCommand command) {
        return new RetrieveAttributeLiveCommandAnswerBuilderImpl(command);
    }

    @Override
    protected CommandResponse doCreateResponse(
            final Function<ResponseFactory, CommandResponse<?>> createResponseFunction) {
        return createResponseFunction.apply(new ResponseFactoryImpl());
    }

    @ParametersAreNonnullByDefault
    @Immutable
    private final class ResponseFactoryImpl implements ResponseFactory {

        @Nonnull
        @Override
        public RetrieveAttributeResponse retrieved(final JsonValue attributeValue) {
            return RetrieveAttributeResponse.of(command.getThingId(), command.getAttributePointer(), attributeValue,
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse attributeNotAccessibleError() {
            return errorResponse(command.getThingId(), AttributeNotAccessibleException.newBuilder(command.getThingId(),
                    command.getAttributePointer())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

}
