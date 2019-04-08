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
package org.eclipse.ditto.signals.commands.live.query;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;

/**
 * An immutable implementation of {@link RetrieveAttributeLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class RetrieveAttributeLiveCommandImpl extends AbstractQueryLiveCommand<RetrieveAttributeLiveCommand,
        RetrieveAttributeLiveCommandAnswerBuilder> implements RetrieveAttributeLiveCommand {

    private final JsonPointer attributePointer;

    private RetrieveAttributeLiveCommandImpl(final RetrieveAttribute command) {
        super(command);
        attributePointer = command.getAttributePointer();
    }

    /**
     * Returns a new instance of {@code RetrieveAttributeLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link RetrieveAttribute}.
     */
    @Nonnull
    public static RetrieveAttributeLiveCommandImpl of(final Command<?> command) {
        return new RetrieveAttributeLiveCommandImpl((RetrieveAttribute) command);
    }

    @Nonnull
    @Override
    public JsonPointer getAttributePointer() {
        return attributePointer;
    }

    @Override
    public RetrieveAttributeLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(RetrieveAttribute.of(getThingId(), getAttributePointer(), dittoHeaders));
    }

    @Nonnull
    @Override
    public RetrieveAttributeLiveCommandAnswerBuilder answer() {
        return RetrieveAttributeLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
