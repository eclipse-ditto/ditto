/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDesiredProperties;

/**
 * An immutable implementation of {@link DeleteFeatureDesiredPropertiesLiveCommand}.
 *
 * @since 1.4.0
 */
@ParametersAreNonnullByDefault
@Immutable
final class DeleteFeatureDesiredPropertiesLiveCommandImpl
        extends
        AbstractModifyLiveCommand<DeleteFeatureDesiredPropertiesLiveCommand, DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder>
        implements DeleteFeatureDesiredPropertiesLiveCommand {

    private final String featureId;

    private DeleteFeatureDesiredPropertiesLiveCommandImpl(final DeleteFeatureDesiredProperties command) {
        super(command);
        featureId = command.getFeatureId();
    }

    /**
     * Returns a new instance of {@code DeleteFeatureDesiredPropertiesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeatureDesiredProperties}.
     */
    @Nonnull
    public static DeleteFeatureDesiredPropertiesLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeatureDesiredPropertiesLiveCommandImpl((DeleteFeatureDesiredProperties) command);
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
    public DeleteFeatureDesiredPropertiesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeatureDesiredPropertiesLiveCommandImpl(DeleteFeatureDesiredProperties.of(getThingEntityId(), getFeatureId(),
                dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder answer() {
        return DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
