/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;

/**
 * An immutable implementation of {@link DeleteAttributeLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class DeleteAttributeLiveCommandImpl
        extends AbstractModifyLiveCommand<DeleteAttributeLiveCommand, DeleteAttributeLiveCommandAnswerBuilder>
        implements DeleteAttributeLiveCommand {

    private final JsonPointer attributePointer;

    private DeleteAttributeLiveCommandImpl(final DeleteAttribute command) {
        super(command);
        attributePointer = command.getAttributePointer();
    }

    /**
     * Returns a new instance of {@code DeleteAttributeLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteAttribute}.
     */
    @Nonnull
    public static DeleteAttributeLiveCommandImpl of(final Command<?> command) {
        return new DeleteAttributeLiveCommandImpl((DeleteAttribute) command);
    }

    @Override
    public JsonPointer getAttributePointer() {
        return attributePointer;
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteAttributeLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteAttributeLiveCommandImpl(
                DeleteAttribute.of(getThingEntityId(), attributePointer, dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteAttributeLiveCommandAnswerBuilder answer() {
        return DeleteAttributeLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
