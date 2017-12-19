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
package org.eclipse.ditto.services.models.streaming;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * A {@link org.eclipse.ditto.signals.base.JsonParsableRegistry} aware of all messages related to streaming.
 */
@Immutable
public final class StreamingRegistry extends AbstractJsonParsableRegistry<Jsonifiable> {

    /**
     * Constructs a new {@code StreamingRegistry} for the specified {@code parseStrategies}.
     *
     * @param parseStrategies the parse strategies.
     */
    private StreamingRegistry(final Map<String, JsonParsable<Jsonifiable>> parseStrategies) {
        super(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValue(Command.JsonFields.TYPE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(Command.JsonFields.TYPE.getPointer().toString())
                        .build());
    }

    /**
     * Returns a new {@code StreamingRegistry}.
     *
     * @return the command registry.
     */
    public static StreamingRegistry newInstance() {
        final Map<String, JsonParsable<Jsonifiable>> parseStrategies = new HashMap<>();

        parseStrategies.put(SudoStreamModifiedEntities.TYPE, SudoStreamModifiedEntities::fromJson);

        return new StreamingRegistry(parseStrategies);
    }
}
