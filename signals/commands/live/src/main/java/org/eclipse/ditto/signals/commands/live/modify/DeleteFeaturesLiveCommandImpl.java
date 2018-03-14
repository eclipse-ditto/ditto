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
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;

/**
 * An immutable implementation of {@link DeleteFeaturesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class DeleteFeaturesLiveCommandImpl
        extends AbstractModifyLiveCommand<DeleteFeaturesLiveCommand, DeleteFeaturesLiveCommandAnswerBuilder>
        implements DeleteFeaturesLiveCommand {

    private DeleteFeaturesLiveCommandImpl(final DeleteFeatures command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code DeleteFeaturesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeatures}.
     */
    @Nonnull
    public static DeleteFeaturesLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeaturesLiveCommandImpl((DeleteFeatures) command);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteFeaturesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeaturesLiveCommandImpl(DeleteFeatures.of(getThingId(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeaturesLiveCommandAnswerBuilder answer() {
        return DeleteFeaturesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
