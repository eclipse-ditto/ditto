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
package org.eclipse.ditto.signals.commands.base;

import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

/**
 * Abstract implementation of {@link CommandRegistry}.
 *
 * @param <T> the type of the Command to parse.
 */
@Immutable
public abstract class AbstractCommandRegistry<T extends Command> extends AbstractJsonParsableRegistry<T>
        implements CommandRegistry<T> {

    /**
     * Constructs a new {@code AbstractCommandRegistry} for the specified {@code parseStrategies}.
     *
     * @param parseStrategies the parse strategies.
     */
    protected AbstractCommandRegistry(final Map<String, JsonParsable<T>> parseStrategies) {
        super(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        final Supplier<String> command = () -> jsonObject.getValue(Command.JsonFields.ID)
                .map(cmd -> getTypePrefix() + cmd) // and transform to V2 format
                // fail if "command" also is not present
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(Command.JsonFields.TYPE.getPointer().toString())
                        .build());

        // if type was not present (was included in V2) take "command" instead
        return jsonObject.getValue(Command.JsonFields.TYPE).orElseGet(command);
    }

    /**
     * Returns the type prefix to use when falling back to determining the type via the "old" V1.
     *
     * @return the type prefix to use.
     */
    protected abstract String getTypePrefix();
}
