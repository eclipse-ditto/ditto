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
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;

/**
 * An immutable implementation of {@link ModifyFeaturePropertiesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyFeaturePropertiesLiveCommandImpl
        extends
        AbstractModifyLiveCommand<ModifyFeaturePropertiesLiveCommand, ModifyFeaturePropertiesLiveCommandAnswerBuilder>
        implements ModifyFeaturePropertiesLiveCommand {

    private final String featureId;
    private final FeatureProperties featureProperties;

    private ModifyFeaturePropertiesLiveCommandImpl(final ModifyFeatureProperties command) {
        super(command);
        featureId = command.getFeatureId();
        featureProperties = command.getProperties();
    }

    /**
     * Returns a new instance of {@code ModifyFeaturePropertiesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyFeatureProperties}.
     */
    @Nonnull
    public static ModifyFeaturePropertiesLiveCommandImpl of(final Command<?> command) {
        return new ModifyFeaturePropertiesLiveCommandImpl((ModifyFeatureProperties) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Nonnull
    @Override
    public FeatureProperties getProperties() {
        return featureProperties;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeaturePropertiesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeaturePropertiesLiveCommandImpl(ModifyFeatureProperties.of(getThingId(), getFeatureId(),
                getProperties(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyFeaturePropertiesLiveCommandAnswerBuilder answer() {
        return ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
