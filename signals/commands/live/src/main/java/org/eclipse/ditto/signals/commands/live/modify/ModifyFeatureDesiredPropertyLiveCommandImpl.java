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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredProperty;

/**
 * An immutable implementation of {@link ModifyFeatureDesiredPropertyLiveCommand}.
 *
 * @since 1.4.0
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyFeatureDesiredPropertyLiveCommandImpl
        extends
        AbstractModifyLiveCommand<ModifyFeatureDesiredPropertyLiveCommand, ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder>
        implements ModifyFeatureDesiredPropertyLiveCommand {

    private final String featureId;
    private final JsonPointer desiredPropertyPointer;
    private final JsonValue desiredPropertyValue;

    private ModifyFeatureDesiredPropertyLiveCommandImpl(final ModifyFeatureDesiredProperty command) {
        super(command);
        featureId = command.getFeatureId();
        desiredPropertyPointer = command.getDesiredPropertyPointer();
        desiredPropertyValue = command.getDesiredPropertyValue();
    }

    /**
     * Returns a new instance of {@code ModifyFeatureDesiredPropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyFeatureDesiredProperty}.
     */
    @Nonnull
    public static ModifyFeatureDesiredPropertyLiveCommandImpl of(final Command<?> command) {
        return new ModifyFeatureDesiredPropertyLiveCommandImpl((ModifyFeatureDesiredProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Nonnull
    @Override
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    @Nonnull
    @Override
    public JsonValue getDesiredPropertyValue() {
        return desiredPropertyValue;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeatureDesiredPropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeatureDesiredPropertyLiveCommandImpl(ModifyFeatureDesiredProperty.of(getThingEntityId(), getFeatureId(),
                getDesiredPropertyPointer(), getDesiredPropertyValue(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder answer() {
        return ModifyFeatureDesiredPropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
