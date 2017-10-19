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

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * RetrieveThingsLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveThingsLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<RetrieveThingsLiveCommand, RetrieveThingsLiveCommandAnswerBuilder.ResponseFactory>
        implements RetrieveThingsLiveCommandAnswerBuilder {

    private RetrieveThingsLiveCommandAnswerBuilderImpl(final RetrieveThingsLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveThingsLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveThingsLiveCommandAnswerBuilderImpl newInstance(final RetrieveThingsLiveCommand command) {
        return new RetrieveThingsLiveCommandAnswerBuilderImpl(command);
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
        public RetrieveThingsResponse retrieved(final List<Thing> things, final Predicate<JsonField> predicate) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            String namespace = command.getNamespace().orElse(null);

            final Function<JsonFieldSelector, RetrieveThingsResponse> fieldSelectorToResponse =
                    fieldSelector -> RetrieveThingsResponse.of(things, fieldSelector, predicate, namespace, dittoHeaders);

            return command.getSelectedFields()
                    .map(fieldSelectorToResponse)
                    .orElse(RetrieveThingsResponse.of(things, predicate, namespace, dittoHeaders));
        }

        @Nonnull
        @Override
        public RetrieveThingsResponse retrieved(final List<Thing> things) {
            return retrieved(things, jsonField -> true);
        }
    }

}
