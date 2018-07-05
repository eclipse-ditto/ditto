/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

class ImmutableResult implements ReceiveStrategy.Result {

    @Nullable private final ThingEvent eventToPersist;
    @Nullable private final ThingCommandResponse response;
    @Nullable private final DittoRuntimeException exception;

    private ImmutableResult(@Nullable final ThingEvent eventToPersist,
            @Nullable final ThingCommandResponse response,
            @Nullable final DittoRuntimeException exception) {
        this.eventToPersist = eventToPersist;
        this.response = response;
        this.exception = exception;
    }

    static ReceiveStrategy.Result of(final ThingEvent eventToPersist, final ThingCommandResponse response) {
        return new ImmutableResult(eventToPersist, response, null);
    }

    @Override
    public Optional<ThingEvent> getEventToPersist() {
        return Optional.ofNullable(eventToPersist);
    }

    @Override
    public Optional<ThingCommandResponse> getResponse() {
        return Optional.ofNullable(response);
    }

    @Override
    public Optional<DittoRuntimeException> getException() {
        return Optional.ofNullable(exception);
    }
}