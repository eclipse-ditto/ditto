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
package org.eclipse.ditto.signals.commands.live.modify;

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link
 * org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition} commands.
 */
public interface DeleteFeatureDefinitionLiveCommandAnswerBuilder
        extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<DeleteFeatureDefinitionLiveCommandAnswerBuilder.ResponseFactory,
                DeleteFeatureDefinitionLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to
     * {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a {@link DeleteFeatureDefinitionResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        DeleteFeatureDefinitionResponse deleted();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Feature Definition was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException
         * FeaturePropertiesNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featureDefinitionNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Feature Definition was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotModifiableException
         * FeaturePropertiesNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featureDefinitionNotModifiableError();

    }

    /**
     * Factory for events triggered by {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition}
     * command.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeatureDefinitionDeleted} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDefinitionDeleted deleted();

    }

}
