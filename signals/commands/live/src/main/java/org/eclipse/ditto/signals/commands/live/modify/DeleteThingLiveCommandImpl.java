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
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;

/**
 * An immutable implementation of {@link DeleteThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class DeleteThingLiveCommandImpl extends AbstractModifyLiveCommand<DeleteThingLiveCommand,
        DeleteThingLiveCommandAnswerBuilder> implements DeleteThingLiveCommand {

    private DeleteThingLiveCommandImpl(final DeleteThing command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code DeleteThingLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteThing}.
     */
    @Nonnull
    public static DeleteThingLiveCommandImpl of(final Command<?> command) {
        return new DeleteThingLiveCommandImpl((DeleteThing) command);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteThingLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteThingLiveCommandImpl(DeleteThing.of(getThingId(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteThingLiveCommandAnswerBuilder answer() {
        return DeleteThingLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
