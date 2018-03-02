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
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;

/**
 * An immutable implementation of {@link DeleteFeatureLiveCommand}.
 */
@Immutable
final class DeleteFeatureLiveCommandImpl
        extends AbstractModifyLiveCommand<DeleteFeatureLiveCommand, DeleteFeatureLiveCommandAnswerBuilder>
        implements DeleteFeatureLiveCommand {

    private final String featureId;

    private DeleteFeatureLiveCommandImpl(final DeleteFeature command) {
        super(command);
        featureId = command.getFeatureId();
    }

    /**
     * Returns a new instance of {@code DeleteFeatureLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeature}.
     */
    @Nonnull
    public static DeleteFeatureLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeatureLiveCommandImpl((DeleteFeature) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteFeatureLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeatureLiveCommandImpl(DeleteFeature.of(getThingId(), getFeatureId(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeatureLiveCommandAnswerBuilder answer() {
        return DeleteFeatureLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
