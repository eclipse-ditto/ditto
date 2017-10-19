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
package org.eclipse.ditto.signals.commands.live.query;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

/**
 * An immutable implementation of {@link RetrieveThingsLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class RetrieveThingsLiveCommandImpl extends AbstractQueryLiveCommand<RetrieveThingsLiveCommand,
        RetrieveThingsLiveCommandAnswerBuilder> implements RetrieveThingsLiveCommand {

    private final List<String> thingIds;
    @Nullable private final String namespace;

    private RetrieveThingsLiveCommandImpl(final RetrieveThings command) {
        super(command);
        thingIds = command.getThingIds();
        namespace = command.getNamespace().orElse(null);
    }

    /**
     * Returns an instance of {@code RetrieveThingsLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link RetrieveThings}.
     */
    @Nonnull
    public static RetrieveThingsLiveCommandImpl of(final Command<?> command) {
        return new RetrieveThingsLiveCommandImpl((RetrieveThings) command);
    }

    @Nonnull
    @Override
    public List<String> getThingIds() {
        return thingIds;
    }

    @Nonnull
    @Override
    public Optional<String> getNamespace() {
        return Optional.ofNullable(namespace);
    }

    @Override
    public RetrieveThingsLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        final RetrieveThings retrieveThingsCommand = RetrieveThings.getBuilder(getThingIds())
                .dittoHeaders(dittoHeaders)
                .selectedFields(getSelectedFields().orElse(null))
                .build();

        return RetrieveThingsLiveCommandImpl.of(retrieveThingsCommand);
    }

    @Nonnull
    @Override
    public RetrieveThingsLiveCommandAnswerBuilder answer() {
        return RetrieveThingsLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + ", namespace=" + namespace + "]";
    }

}
