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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

final class ImmutableResult implements CommandStrategy.Result {

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
    public void apply(final BiConsumer<ThingModifiedEvent, Consumer<ThingModifiedEvent>> persistConsumer,
            final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {
        if (eventToPersist != null && response != null) {
            persistConsumer.accept(eventToPersist, event -> {
                notifyConsumer.accept(response);
                if (becomeDeleted) {
                    becomeDeletedRunnable.run();
                }
            });
        } else if (response != null) {
            notifyConsumer.accept(response);
        } else if (exception != null) {
            notifyConsumer.accept(exception);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableResult that = (ImmutableResult) o;
        return becomeDeleted == that.becomeDeleted &&
                Objects.equals(eventToPersist, that.eventToPersist) &&
                Objects.equals(response, that.response) &&
                Objects.equals(exception, that.exception);
    }

    @Override
    public int hashCode() {

        return Objects.hash(eventToPersist, response, exception, becomeDeleted);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "eventToPersist=" + eventToPersist +
                ", response=" + response +
                ", exception=" + exception +
                ", becomeDeleted=" + becomeDeleted +
                "]";
    }
}