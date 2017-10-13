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
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveThings} commands.
 */
public interface RetrieveThingsLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveThingsLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveThings} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a success response containing the retrieved value for the {@link RetrieveThings} command.
         *
         * @param things the value of the requested Things.
         * @param predicate a predicate determining which fields from the provided Things should be included in the
         * response.
         * @return the response.
         * @throws NullPointerException if any argument is {@code null}.
         */
        @Nonnull
        RetrieveThingsResponse retrieved(List<Thing> things, Predicate<JsonField> predicate);

        /**
         * Creates a success response containing the retrieved value for the {@link RetrieveThings} command.
         *
         * @param things the value of the requested Things.
         * @return the response.
         * @throws NullPointerException if {@code things} is {@code null}.
         */
        @Nonnull
        RetrieveThingsResponse retrieved(List<Thing> things);
    }

}
