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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link DeleteFeatureProperty}
 * commands.
 */
public interface DeleteFeaturePropertyLiveCommandAnswerBuilder
        extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<DeleteFeaturePropertyLiveCommandAnswerBuilder.ResponseFactory,
                        DeleteFeaturePropertyLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link DeleteFeatureProperty} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a {@link DeleteFeaturePropertyResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        DeleteFeaturePropertyResponse deleted();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature property was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException
         * FeaturePropertyNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featurePropertyNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature property was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotModifiableException
         * FeaturePropertyNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featurePropertyNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link DeleteFeatureProperty} command.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeaturePropertyDeleted} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeaturePropertyDeleted deleted();
    }

}
