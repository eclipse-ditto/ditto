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
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;

/**
 * An immutable implementation of {@link ModifyFeatureLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyFeatureLiveCommandImpl extends AbstractModifyLiveCommand<ModifyFeatureLiveCommand,
        ModifyFeatureLiveCommandAnswerBuilder> implements ModifyFeatureLiveCommand {

    private final Feature feature;

    private ModifyFeatureLiveCommandImpl(final ModifyFeature command) {
        super(command);
        feature = command.getFeature();
    }

    /**
     * Returns a new instance of {@code ModifyFeatureLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyFeature}.
     */
    @Nonnull
    public static ModifyFeatureLiveCommandImpl of(final Command<?> command) {
        return new ModifyFeatureLiveCommandImpl((ModifyFeature) command);
    }

    @Override
    public String getFeatureId() {
        return feature.getId();
    }

    @Nonnull
    @Override
    public Feature getFeature() {
        return feature;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeatureLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeatureLiveCommandImpl(ModifyFeature.of(getThingId(), getFeature(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyFeatureLiveCommandAnswerBuilder answer() {
        return ModifyFeatureLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
