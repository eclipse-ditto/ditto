/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.things.commands.sudo;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandResponseRegistry} aware of all {@link SudoCommandResponse}s.
 */
@Immutable
public class SudoCommandResponseRegistry extends AbstractCommandResponseRegistry<SudoCommandResponse> {

    /**
     * Constructs a new {@code SudoCommandResponseRegistry} for the specified {@code parseStrategies}.
     *
     * @param parseStrategies the parse strategies.
     */
    protected SudoCommandResponseRegistry(final Map<String, JsonParsable<SudoCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code SudoCommandResponseRegistry}.
     *
     * @return the command registry.
     */
    public static SudoCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<SudoCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(SudoRetrieveThingResponse.TYPE, SudoRetrieveThingResponse::fromJson);
        parseStrategies.put(SudoRetrieveThingsResponse.TYPE, SudoRetrieveThingsResponse::fromJson);

        return new SudoCommandResponseRegistry(parseStrategies);
    }
}
