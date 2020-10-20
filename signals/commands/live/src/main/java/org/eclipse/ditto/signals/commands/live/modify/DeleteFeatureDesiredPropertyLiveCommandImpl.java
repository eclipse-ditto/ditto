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
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDesiredProperty;

/**
 * An immutable implementation of {@link DeleteFeatureDesiredPropertyLiveCommand}.
 * 
 * @since 1.4.0
 */
@Immutable
final class DeleteFeatureDesiredPropertyLiveCommandImpl
        extends AbstractModifyLiveCommand<DeleteFeatureDesiredPropertyLiveCommand, DeleteFeatureDesiredPropertyLiveCommandAnswerBuilder>
        implements DeleteFeatureDesiredPropertyLiveCommand {

    private final String featureId;
    private final JsonPointer desiredPropertyPointer;

    /**
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    private DeleteFeatureDesiredPropertyLiveCommandImpl(final DeleteFeatureDesiredProperty command) {
        super(command);
        featureId = command.getFeatureId();
        desiredPropertyPointer = command.getDesiredPropertyPointer();
    }

    /**
     * Returns a new instance of {@code DeleteFeatureDesiredPropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeatureDesiredProperty}.
     */
    @Nonnull
    public static DeleteFeatureDesiredPropertyLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeatureDesiredPropertyLiveCommandImpl((DeleteFeatureDesiredProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteFeatureDesiredPropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeatureDesiredPropertyLiveCommandImpl(DeleteFeatureDesiredProperty.of(getThingEntityId(), getFeatureId(),
                getDesiredPropertyPointer(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeatureDesiredPropertyLiveCommandAnswerBuilder answer() {
        return DeleteFeatureDesiredPropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
