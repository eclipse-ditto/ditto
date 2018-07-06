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

package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

class ImmutableResult implements CommandStrategy.Result {

    @Nullable private final ThingModifiedEvent eventToPersist;
    @Nullable private final WithDittoHeaders response;
    @Nullable private final DittoRuntimeException exception;
    private final boolean becomeDeleted;

    private ImmutableResult(@Nullable final ThingModifiedEvent eventToPersist,
            @Nullable final WithDittoHeaders response,
            @Nullable final DittoRuntimeException exception) {
        this.eventToPersist = eventToPersist;
        this.response = response;
        this.exception = exception;
        this.becomeDeleted = false;
    }

    private ImmutableResult(@Nullable final ThingModifiedEvent eventToPersist,
            @Nullable final WithDittoHeaders response,
            @Nullable final DittoRuntimeException exception,
            final boolean becomeDeleted) {
        this.eventToPersist = eventToPersist;
        this.response = response;
        this.exception = exception;
        this.becomeDeleted = becomeDeleted;
    }

    static CommandStrategy.Result of(final ThingModifiedEvent eventToPersist,
            final WithDittoHeaders response,
            final DittoRuntimeException dittoRuntimeException,
            final boolean becomeDeleted) {
        return new ImmutableResult(eventToPersist, response, dittoRuntimeException, becomeDeleted);
    }

    static CommandStrategy.Result of(final ThingModifiedEvent eventToPersist, final WithDittoHeaders response) {
        return new ImmutableResult(eventToPersist, response, null);
    }

    static CommandStrategy.Result of(final DittoRuntimeException dittoRuntimeException) {
        return new ImmutableResult(null, null, dittoRuntimeException);
    }

    static CommandStrategy.Result of(final WithDittoHeaders response) {
        return new ImmutableResult(null, response, null);
    }

    static CommandStrategy.Result empty() {
        return new ImmutableResult(null, null, null);
    }

    @Override
    public boolean isBecomeDeleted() {
        return becomeDeleted;
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