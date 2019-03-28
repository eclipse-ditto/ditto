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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link ThingSearchSudoCommand}s.
 */
@Immutable
public final class ThingSearchSudoCommandRegistry extends AbstractCommandRegistry<ThingSearchSudoCommand> {

    private ThingSearchSudoCommandRegistry(final Map<String, JsonParsable<ThingSearchSudoCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code SearchSudoCommandRegistry}.
     *
     * @return the command registry.
     */
    public static ThingSearchSudoCommandRegistry newInstance() {
        final Map<String, JsonParsable<ThingSearchSudoCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(SudoCountThings.TYPE, SudoCountThings::fromJson);
        parseStrategies.put(SudoRetrieveNamespaceReport.TYPE, SudoRetrieveNamespaceReport::fromJson);

        return new ThingSearchSudoCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ThingSearchSudoCommand.TYPE_PREFIX;
    }

    @Override
    public ThingSearchSudoCommand parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        try {
            return super.parse(jsonObject, dittoHeaders);
        } catch (final JsonRuntimeException | IllegalArgumentException | NullPointerException | ClassCastException e) {
            throw new DittoJsonException(e, dittoHeaders);
        }
    }
}
