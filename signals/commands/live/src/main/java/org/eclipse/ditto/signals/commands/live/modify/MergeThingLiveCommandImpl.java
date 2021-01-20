/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;

/**
 * An immutable implementation of {@link MergeThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class MergeThingLiveCommandImpl extends AbstractModifyLiveCommand<MergeThingLiveCommand,
        MergeThingLiveCommandAnswerBuilder> implements MergeThingLiveCommand {

    private final JsonPointer path;
    private final JsonValue value;

    private MergeThingLiveCommandImpl(final MergeThing command) {
        super(command);
        path = command.getPath();
        value = command.getValue();
    }

    /**
     * Returns a new instance of {@code MergeThingLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.modify.MergeThing}.
     */
    @Nonnull
    public static MergeThingLiveCommandImpl of(final Command<?> command) {
        return new MergeThingLiveCommandImpl((MergeThing) command);
    }

    @Override
    public JsonPointer getPath() {
        return path;
    }

    @Override
    public JsonValue getValue() {
        return value;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public MergeThingLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new MergeThingLiveCommandImpl(MergeThing.of(getThingEntityId(), getPath(), getValue(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public MergeThingLiveCommandAnswerBuilder answer() {
        return MergeThingLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
