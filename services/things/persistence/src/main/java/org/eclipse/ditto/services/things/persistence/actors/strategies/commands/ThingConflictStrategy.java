/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

/**
 * This strategy handles the {@link CreateThing} command for an already existing Thing.
 */
@Immutable
final class ThingConflictStrategy extends AbstractCommandStrategy<CreateThing> {

    /**
     * Constructs a new {@code ThingConflictStrategy} object.
     */
    ThingConflictStrategy() {
        super(CreateThing.class);
    }

    @Override
    public boolean isDefined(final Context context, @Nullable final Thing thing,
            final CreateThing command) {
        return Objects.equals(context.getThingId(), command.getId());
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final CreateThing command) {
        return ResultFactory.newErrorResult(ThingConflictException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
    }

}
