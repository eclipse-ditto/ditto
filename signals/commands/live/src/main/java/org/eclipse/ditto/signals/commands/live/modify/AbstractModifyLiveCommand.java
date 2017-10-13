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

import java.util.Optional;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.live.base.AbstractLiveCommand;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;

/**
 * Abstract base implementation for LiveCommands wrapping {@link ThingModifyCommand}s.
 *
 * @param <T> the type of the LiveCommand; currently needed as return type for {@link
 * #setDittoHeaders(org.eclipse.ditto.model.base.headers.DittoHeaders)}.
 * @param <B> the type of the LiveCommandAnswerBuilder to be returned for {@link #answer()}.
 */
abstract class AbstractModifyLiveCommand<T extends LiveCommand<T, B> & ThingModifyCommand<T>, B extends LiveCommandAnswerBuilder>
        extends AbstractLiveCommand<T, B> implements ThingModifyCommand<T> {

    private final ThingModifyCommand<?> thingModifyCommand;

    /**
     * Constructs a new {@code AbstractModifyLiveCommand} object.
     *
     * @param thingModifyCommand the command to be wrapped by the returned object.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    protected AbstractModifyLiveCommand(final ThingModifyCommand<?> thingModifyCommand) {
        super(thingModifyCommand);
        this.thingModifyCommand = thingModifyCommand;
    }

    @Override
    public String getThingId() {
        return thingModifyCommand.getThingId();
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return thingModifyCommand.getEntity(schemaVersion);
    }

}
