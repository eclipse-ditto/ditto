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
package org.eclipse.ditto.signals.commands.live.modify;

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link DeleteAttributes}
 * commands.
 */
public interface DeleteAttributesLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<DeleteAttributesLiveCommandAnswerBuilder.ResponseFactory,
                DeleteAttributesLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link DeleteAttributes} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a {@link DeleteAttributesResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        DeleteAttributesResponse deleted();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the attributes were not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException
         * AttributesNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse attributesNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the attributes were not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotModifiableException
         * AttributesNotModifiableException
         */
        @Nonnull
        ThingErrorResponse attributesNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link DeleteAttributes} command.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates an {@link AttributesDeleted} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        AttributesDeleted deleted();
    }

}
