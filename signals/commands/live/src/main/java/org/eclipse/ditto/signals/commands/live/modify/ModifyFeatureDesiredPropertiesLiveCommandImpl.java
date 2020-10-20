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
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredProperties;

/**
 * An immutable implementation of {@link ModifyFeatureDesiredPropertiesLiveCommand}.
 *
 * @since 1.4.0
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyFeatureDesiredPropertiesLiveCommandImpl
        extends
        AbstractModifyLiveCommand<ModifyFeatureDesiredPropertiesLiveCommand, ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder>
        implements ModifyFeatureDesiredPropertiesLiveCommand {

    private final String featureId;
    private final FeatureProperties desiredProperties;

    private ModifyFeatureDesiredPropertiesLiveCommandImpl(final ModifyFeatureDesiredProperties command) {
        super(command);
        featureId = command.getFeatureId();
        desiredProperties = command.getDesiredProperties();
    }


    /**
     * Returns a new instance of {@code ModifyFeatureDesiredPropertiesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyFeatureDesiredProperties}.
     */
    @Nonnull
    public static ModifyFeatureDesiredPropertiesLiveCommandImpl of(final Command<?> command) {
        return new ModifyFeatureDesiredPropertiesLiveCommandImpl((ModifyFeatureDesiredProperties) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Nonnull
    @Override
    public FeatureProperties getDesiredProperties() {
        return desiredProperties;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeatureDesiredPropertiesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeatureDesiredPropertiesLiveCommandImpl(
                ModifyFeatureDesiredProperties.of(getThingEntityId(), getFeatureId(),
                        getDesiredProperties(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder answer() {
        return ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
