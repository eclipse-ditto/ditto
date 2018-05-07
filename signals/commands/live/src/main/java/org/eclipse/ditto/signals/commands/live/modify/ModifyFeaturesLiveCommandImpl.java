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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;

/**
 * An immutable implementation of {@link ModifyFeaturesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyFeaturesLiveCommandImpl extends AbstractModifyLiveCommand<ModifyFeaturesLiveCommand,
        ModifyFeaturesLiveCommandAnswerBuilder> implements ModifyFeaturesLiveCommand {

    private final Features features;

    private ModifyFeaturesLiveCommandImpl(final ModifyFeatures command) {
        super(command);
        features = command.getFeatures();
    }

    /**
     * Returns a new instance of {@code ModifyFeaturesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyFeatures}.
     */
    @Nonnull
    public static ModifyFeaturesLiveCommandImpl of(final Command<?> command) {
        return new ModifyFeaturesLiveCommandImpl((ModifyFeatures) command);
    }

    @Nonnull
    @Override
    public Features getFeatures() {
        return features;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeaturesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeaturesLiveCommandImpl(ModifyFeatures.of(getThingId(), getFeatures(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyFeaturesLiveCommandAnswerBuilder answer() {
        return ModifyFeaturesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
