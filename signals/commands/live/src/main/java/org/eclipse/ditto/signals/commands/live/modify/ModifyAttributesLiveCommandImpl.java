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
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;

/**
 * An immutable implementation of {@link ModifyAttributesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyAttributesLiveCommandImpl extends AbstractModifyLiveCommand<ModifyAttributesLiveCommand,
        ModifyAttributesLiveCommandAnswerBuilder> implements ModifyAttributesLiveCommand {

    private final Attributes attributes;

    private ModifyAttributesLiveCommandImpl(final ModifyAttributes command) {
        super(command);
        attributes = command.getAttributes();
    }

    /**
     * Returns a new instance of {@code ModifyAttributesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyAttributes}.
     */
    @Nonnull
    public static ModifyAttributesLiveCommandImpl of(final Command<?> command) {
        return new ModifyAttributesLiveCommandImpl((ModifyAttributes) command);
    }

    @Nonnull
    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyAttributesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyAttributesLiveCommandImpl(ModifyAttributes.of(getThingId(), getAttributes(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyAttributesLiveCommandAnswerBuilder answer() {
        return ModifyAttributesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
