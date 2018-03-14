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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * An immutable implementation of {@link DeleteFeatureDefinitionLiveCommand}.
 */
@AllValuesAreNonnullByDefault
@Immutable
final class DeleteFeatureDefinitionLiveCommandImpl
        extends
        AbstractModifyLiveCommand<DeleteFeatureDefinitionLiveCommand, DeleteFeatureDefinitionLiveCommandAnswerBuilder>
        implements DeleteFeatureDefinitionLiveCommand {

    private final String featureId;

    private DeleteFeatureDefinitionLiveCommandImpl(final DeleteFeatureDefinition command) {
        super(command);
        featureId = command.getFeatureId();
    }

    /**
     * Returns a new instance of {@code DeleteFeatureDefinitionLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeatureDefinition}.
     */
    @Nonnull
    public static DeleteFeatureDefinitionLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeatureDefinitionLiveCommandImpl((DeleteFeatureDefinition) command);
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
    public DeleteFeatureDefinitionLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeatureDefinitionLiveCommandImpl(DeleteFeatureDefinition.of(getThingId(), getFeatureId(),
                dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeatureDefinitionLiveCommandAnswerBuilder answer() {
        return DeleteFeatureDefinitionLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
