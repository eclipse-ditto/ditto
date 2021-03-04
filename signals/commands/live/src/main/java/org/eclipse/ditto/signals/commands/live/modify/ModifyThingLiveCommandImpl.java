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
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;

/**
 * An immutable implementation of {@link ModifyThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyThingLiveCommandImpl extends AbstractModifyLiveCommand<ModifyThingLiveCommand,
        ModifyThingLiveCommandAnswerBuilder> implements ModifyThingLiveCommand {

    private final Thing thing;

    private ModifyThingLiveCommandImpl(final ModifyThing command) {
        super(command);
        thing = command.getThing();
    }

    /**
     * Returns a new instance of {@code ModifyThingLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyThing}.
     */
    @Nonnull
    public static ModifyThingLiveCommandImpl of(final Command<?> command) {
        return new ModifyThingLiveCommandImpl((ModifyThing) command);
    }

    @Nonnull
    @Override
    public Thing getThing() {
        return thing;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyThingLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyThingLiveCommandImpl(ModifyThing.of(getThingEntityId(), getThing(), null, dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyThingLiveCommandAnswerBuilder answer() {
        return ModifyThingLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
