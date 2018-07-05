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

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.BiFunction;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

/**
 * This strategy handles the {@link CreateThing} command for an already existing Thing.
 */
@NotThreadSafe
public final class ThingConflictStrategy extends AbstractReceiveStrategy<CreateThing> {

    /**
     * Constructs a new {@code ThingConflictStrategy} object.
     */
    public ThingConflictStrategy() {
        super(CreateThing.class);
    }

    @Override
    public BiFunction<Context, CreateThing, Boolean> getPredicate() {
        return (ctx, command) -> Objects.equals(ctx.getThingId(), command.getId());
    }

    @Override
    protected Result doApply(final Context context, final CreateThing command) {
        return ImmutableResult.of(ThingConflictException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
    }

    @Override
    public BiFunction<Context, CreateThing, Result> getUnhandledFunction() {
        return (ctx, command) -> {
            throw new IllegalArgumentException(
                    MessageFormat.format(ThingPersistenceActor.UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
        };
    }
}