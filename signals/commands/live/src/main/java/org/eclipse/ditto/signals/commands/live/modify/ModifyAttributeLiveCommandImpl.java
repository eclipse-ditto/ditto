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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;

/**
 * An immutable implementation of {@link ModifyAttributeLiveCommand}.
 */
@Immutable
final class ModifyAttributeLiveCommandImpl extends AbstractModifyLiveCommand<ModifyAttributeLiveCommand,
        ModifyAttributeLiveCommandAnswerBuilder> implements ModifyAttributeLiveCommand {

    private final JsonPointer attributePointer;
    private final JsonValue attributeValue;

    private ModifyAttributeLiveCommandImpl(final ModifyAttribute command) {
        super(command);
        attributePointer = command.getAttributePointer();
        attributeValue = command.getAttributeValue();
    }

    /**
     * Returns a new instance of {@code ModifyAttributeLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyAttribute}.
     */
    @Nonnull
    public static ModifyAttributeLiveCommandImpl of(final Command<?> command) {
        return new ModifyAttributeLiveCommandImpl((ModifyAttribute) command);
    }

    @Nonnull
    @Override
    public JsonPointer getAttributePointer() {
        return attributePointer;
    }

    @Nonnull
    @Override
    public JsonValue getAttributeValue() {
        return attributeValue;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyAttributeLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyAttributeLiveCommandImpl(ModifyAttribute.of(getThingId(), getAttributePointer(),
                getAttributeValue(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyAttributeLiveCommandAnswerBuilder answer() {
        return ModifyAttributeLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
