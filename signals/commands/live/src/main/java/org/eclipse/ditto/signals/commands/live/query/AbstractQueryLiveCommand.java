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

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.live.base.AbstractLiveCommand;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * Abstract base implementation for LiveCommands wrapping {@link ThingQueryCommand}s.
 *
 * @param <T> the type of the LiveCommand; currently needed as return type for {@link #setDittoHeaders(DittoHeaders)}.
 * @param <B> the type of the LiveCommandAnswerBuilder to be returned for {@link #answer()}.
 */
@Immutable
abstract class AbstractQueryLiveCommand<T extends LiveCommand<T, B> & ThingQueryCommand<T>, B extends LiveCommandAnswerBuilder>
        extends AbstractLiveCommand<T, B> implements ThingQueryCommand<T> {

    private final ThingQueryCommand<?> thingQueryCommand;

    /**
     * Constructs a new {@code AbstractQueryLiveCommand} object.
     *
     * @param thingQueryCommand the command to be wrapped by the returned object.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    protected AbstractQueryLiveCommand(final ThingQueryCommand<?> thingQueryCommand) {
        super(thingQueryCommand);
        this.thingQueryCommand = thingQueryCommand;
    }

    @Override
    public String getThingId() {
        return thingQueryCommand.getThingId();
    }

    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return thingQueryCommand.getSelectedFields();
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

}
