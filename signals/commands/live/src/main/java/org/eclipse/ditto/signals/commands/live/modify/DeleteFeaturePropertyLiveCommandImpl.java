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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;

/**
 * An immutable implementation of {@link DeleteFeaturePropertyLiveCommand}.
 */
@Immutable
final class DeleteFeaturePropertyLiveCommandImpl
        extends
        AbstractModifyLiveCommand<DeleteFeaturePropertyLiveCommand, DeleteFeaturePropertyLiveCommandAnswerBuilder>
        implements DeleteFeaturePropertyLiveCommand {

    private final String featureId;
    private final JsonPointer propertyPointer;

    /**
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    private DeleteFeaturePropertyLiveCommandImpl(final DeleteFeatureProperty command) {
        super(command);
        featureId = command.getFeatureId();
        propertyPointer = command.getPropertyPointer();
    }

    /**
     * Returns a new instance of {@code DeleteFeaturePropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeatureProperty}.
     */
    @Nonnull
    public static DeleteFeaturePropertyLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeaturePropertyLiveCommandImpl((DeleteFeatureProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteFeaturePropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeaturePropertyLiveCommandImpl(DeleteFeatureProperty.of(getThingId(), getFeatureId(),
                getPropertyPointer(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeaturePropertyLiveCommandAnswerBuilder answer() {
        return DeleteFeaturePropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
