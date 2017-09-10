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
package org.eclipse.ditto.signals.commands.thingsearch;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandResponseRegistry} aware of all {@link
 * ThingSearchCommandResponse}s.
 */
@Immutable
public final class ThingSearchCommandResponseRegistry extends
        AbstractCommandResponseRegistry<ThingSearchCommandResponse> {

    private ThingSearchCommandResponseRegistry(
            final Map<String, JsonParsable<ThingSearchCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code SearchCommandResponseRegistry}.
     *
     * @return the command response registry.
     */
    public static ThingSearchCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<ThingSearchCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(QueryThingsResponse.TYPE, QueryThingsResponse::fromJson);
        parseStrategies.put(CountThingsResponse.TYPE, CountThingsResponse::fromJson);

        return new ThingSearchCommandResponseRegistry(parseStrategies);
    }

}
