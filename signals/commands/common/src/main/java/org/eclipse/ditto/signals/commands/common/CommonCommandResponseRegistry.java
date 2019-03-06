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
package org.eclipse.ditto.signals.commands.common;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.common.purge.PurgeEntitiesResponse;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link CommonCommandResponse}s.
 */
@Immutable
public class CommonCommandResponseRegistry extends AbstractCommandResponseRegistry<CommonCommandResponse> {

    /**
     * Constructs a new registry for the specified {@code parseStrategies}.
     *
     * @param parseStrategies the parse strategies.
     */
    protected CommonCommandResponseRegistry(final Map<String, JsonParsable<CommonCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new registry.
     *
     * @return the command registry.
     */
    public static CommonCommandResponseRegistry getInstance() {
        final Map<String, JsonParsable<CommonCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(PurgeEntitiesResponse.TYPE, PurgeEntitiesResponse::fromJson);

        return new CommonCommandResponseRegistry(parseStrategies);
    }
}