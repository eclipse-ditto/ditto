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

import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.events.things.ThingCreated;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link CreateThing}
 * commands.
 */
public interface CreateThingLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<CreateThingLiveCommandAnswerBuilder.ResponseFactory,
                CreateThingLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link CreateThing} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a {@link CreateThingResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        CreateThingResponse created();

        /**
         * Creates a {@link ThingErrorResponse} indicating a conflict.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException ThingConflictException
         */
        @Nonnull
        ThingErrorResponse thingConflictError();
    }

    /**
     * Factory for events triggered by {@link CreateThing} commands.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link ThingCreated} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        ThingCreated created();
    }

}
