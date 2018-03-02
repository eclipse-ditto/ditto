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
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

/**
 * An immutable implementation of {@link CreateThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class CreateThingLiveCommandImpl extends AbstractModifyLiveCommand<CreateThingLiveCommand,
        CreateThingLiveCommandAnswerBuilder> implements CreateThingLiveCommand {

    private final Thing thing;

    private CreateThingLiveCommandImpl(final CreateThing command) {
        super(command);
        thing = command.getThing();
    }

    /**
     * Returns a new instance of {@code CreateThingLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link CreateThing}.
     */
    @Nonnull
    public static CreateThingLiveCommand of(final Command<?> command) {
        return new CreateThingLiveCommandImpl((CreateThing) command);
    }

    @Override
    public String getThingId() {
        return thing.getId().orElse(null);
    }

    @Override
    public Thing getThing() {
        return thing;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public CreateThingLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(CreateThing.of(thing, null, dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public CreateThingLiveCommandAnswerBuilder answer() {
        return CreateThingLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
