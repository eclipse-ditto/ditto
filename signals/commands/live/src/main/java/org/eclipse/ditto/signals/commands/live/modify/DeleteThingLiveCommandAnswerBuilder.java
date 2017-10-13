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

import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.events.things.ThingDeleted;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link DeleteThing}
 * commands.
 */
public interface DeleteThingLiveCommandAnswerBuilder extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<
        DeleteThingLiveCommandAnswerBuilder.ResponseFactory, DeleteThingLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link DeleteThing} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a {@link DeleteThingResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        DeleteThingResponse deleted();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Thing was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException
         * ThingNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse thingNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Thing was not deletable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.ThingNotDeletableException
         * ThingNotDeletableException
         */
        @Nonnull
        ThingErrorResponse thingNotDeletableError();
    }

    /**
     * Factory for events triggered by {@link DeleteThing} command.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link ThingDeleted} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        ThingDeleted deleted();
    }

}
