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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;

/**
 * An immutable implementation of {@link DeleteFeaturePropertiesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class DeleteFeaturePropertiesLiveCommandImpl
        extends
        AbstractModifyLiveCommand<DeleteFeaturePropertiesLiveCommand, DeleteFeaturePropertiesLiveCommandAnswerBuilder>
        implements DeleteFeaturePropertiesLiveCommand {

    private final String featureId;

    private DeleteFeaturePropertiesLiveCommandImpl(final DeleteFeatureProperties command) {
        super(command);
        featureId = command.getFeatureId();
    }

    /**
     * Returns a new instance of {@code DeleteFeaturePropertiesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeatureProperties}.
     */
    @Nonnull
    public static DeleteFeaturePropertiesLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeaturePropertiesLiveCommandImpl((DeleteFeatureProperties) command);
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
    public DeleteFeaturePropertiesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeaturePropertiesLiveCommandImpl(DeleteFeatureProperties.of(getThingId(), getFeatureId(),
                dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeaturePropertiesLiveCommandAnswerBuilder answer() {
        return DeleteFeaturePropertiesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
