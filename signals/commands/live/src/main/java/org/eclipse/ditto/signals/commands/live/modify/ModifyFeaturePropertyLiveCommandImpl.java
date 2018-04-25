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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;

/**
 * An immutable implementation of {@link ModifyFeaturePropertyLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyFeaturePropertyLiveCommandImpl
        extends
        AbstractModifyLiveCommand<ModifyFeaturePropertyLiveCommand, ModifyFeaturePropertyLiveCommandAnswerBuilder>
        implements ModifyFeaturePropertyLiveCommand {

    private final String featureId;
    private final JsonPointer propertyPointer;
    private final JsonValue propertyValue;

    private ModifyFeaturePropertyLiveCommandImpl(final ModifyFeatureProperty command) {
        super(command);
        featureId = command.getFeatureId();
        propertyPointer = command.getPropertyPointer();
        propertyValue = command.getPropertyValue();
    }

    /**
     * Returns a new instance of {@code ModifyFeaturePropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyFeatureProperty}.
     */
    @Nonnull
    public static ModifyFeaturePropertyLiveCommandImpl of(final Command<?> command) {
        return new ModifyFeaturePropertyLiveCommandImpl((ModifyFeatureProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Nonnull
    @Override
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    @Nonnull
    @Override
    public JsonValue getPropertyValue() {
        return propertyValue;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeaturePropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeaturePropertyLiveCommandImpl(ModifyFeatureProperty.of(getThingId(), getFeatureId(),
                getPropertyPointer(), getPropertyValue(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyFeaturePropertyLiveCommandAnswerBuilder answer() {
        return ModifyFeaturePropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
