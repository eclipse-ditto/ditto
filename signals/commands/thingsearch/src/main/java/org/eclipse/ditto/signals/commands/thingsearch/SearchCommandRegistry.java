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
package org.eclipse.ditto.signals.commands.thingsearch;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link ThingSearchCommand}s.
 */
@Immutable
public final class SearchCommandRegistry extends AbstractCommandRegistry<ThingSearchCommand> {

    private SearchCommandRegistry(final Map<String, JsonParsable<ThingSearchCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code SearchCommandRegistry}.
     *
     * @return the command registry.
     */
    public static SearchCommandRegistry newInstance() {
        final Map<String, JsonParsable<ThingSearchCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(QueryThings.TYPE, QueryThings::fromJson);
        parseStrategies.put(CountThings.TYPE, CountThings::fromJson);

        return new SearchCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ThingSearchCommand.TYPE_PREFIX;
    }

    @Override
    public ThingSearchCommand parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        try {
            return super.parse(jsonObject, dittoHeaders);
        } catch (final JsonRuntimeException | IllegalArgumentException | NullPointerException | ClassCastException e) {
            throw new DittoJsonException(e, dittoHeaders);
        }
    }
}
