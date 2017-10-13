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

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveAttribute} commands.
 */
public interface RetrieveAttributeLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveAttributeLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveAttribute} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a {@link RetrieveAttributeResponse} containing the retrieved value for the {@link RetrieveAttribute}
         * command.
         *
         * @param attributeValue the value of the requested attribute.
         * @return the response.
         * @throws NullPointerException if {@code attributeValue} is {@code null}
         */
        @Nonnull
        RetrieveAttributeResponse retrieved(JsonValue attributeValue);

        /**
         * Creates a {@link ThingErrorResponse} specifying that the requested attribute does not exist or the requesting
         * user does not have enough permission to retrieve it.
         *
         * @return the response.
         */
        @Nonnull
        ThingErrorResponse attributeNotAccessibleError();
    }

}
