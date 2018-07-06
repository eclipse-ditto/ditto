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
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

class ImmutableResult implements ReceiveStrategy.Result {

    @Nullable private final ThingModifiedEvent eventToPersist;
    @Nullable private final CommandResponse response;
    @Nullable private final DittoRuntimeException exception;
    private final boolean becomeCreated;
    private final boolean becomeDeleted;

    private ImmutableResult(@Nullable final ThingModifiedEvent eventToPersist,
            @Nullable final CommandResponse response,
            @Nullable final DittoRuntimeException exception) {
        this.eventToPersist = eventToPersist;
        this.response = response;
        this.exception = exception;
        this.becomeCreated = false;
        this.becomeDeleted = false;
    }

    private ImmutableResult(@Nullable final ThingModifiedEvent eventToPersist,
            @Nullable final CommandResponse response,
            @Nullable final DittoRuntimeException exception,
            final boolean becomeCreated,
            final boolean becomeDeleted) {
        this.eventToPersist = eventToPersist;
        this.response = response;
        this.exception = exception;
        this.becomeCreated = becomeCreated;
        this.becomeDeleted = becomeDeleted;
    }

    static ReceiveStrategy.Result of(final ThingModifiedEvent eventToPersist,
            final CommandResponse response,
            final DittoRuntimeException dittoRuntimeException,
            final boolean becomeCreated,
            final boolean becomeDeleted) {
        return new ImmutableResult(eventToPersist, response, dittoRuntimeException, becomeCreated, becomeDeleted);
    }

    static ReceiveStrategy.Result of(final ThingModifiedEvent eventToPersist, final CommandResponse response) {
        return new ImmutableResult(eventToPersist, response, null);
    }

    static ReceiveStrategy.Result of(final DittoRuntimeException dittoRuntimeException) {
        return new ImmutableResult(null, null, dittoRuntimeException);
    }

    static ReceiveStrategy.Result of(final CommandResponse response) {
        return new ImmutableResult(null, response, null);
    }

    static ReceiveStrategy.Result empty() {
        return new ImmutableResult(null, null, null);
    }

    @Override
    public boolean isBecomeCreated() {
        return isBecomeCreated();
    }

    @Override
    public boolean isBecomeDeleted() {
        return isBecomeDeleted();
    }

    @Override
    public Optional<ThingModifiedEvent> getEventToPersist() {
        return Optional.ofNullable(eventToPersist);
    }

    @Override
    public Optional<WithDittoHeaders> getResponse() {
        return Optional.ofNullable(response);
    }

    @Override
    public Optional<DittoRuntimeException> getException() {
        return Optional.ofNullable(exception);
    }

}