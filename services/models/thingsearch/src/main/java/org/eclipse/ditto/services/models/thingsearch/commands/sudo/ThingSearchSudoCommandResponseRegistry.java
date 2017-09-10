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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandResponseRegistry} aware of all responses to .
 */
@Immutable
public final class ThingSearchSudoCommandResponseRegistry
        extends AbstractCommandResponseRegistry<CommandResponse> {

    private ThingSearchSudoCommandResponseRegistry(
            final Map<String, JsonParsable<CommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@link ThingSearchSudoCommandResponseRegistry}.
     *
     * @return the command response registry.
     */
    public static ThingSearchSudoCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<CommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(CountThingsResponse.TYPE, CountThingsResponse::fromJson); //returned by SudoCountThings
        parseStrategies.put(SudoRetrieveNamespaceReportResponse.TYPE, SudoRetrieveNamespaceReportResponse::fromJson);

        return new ThingSearchSudoCommandResponseRegistry(parseStrategies);
    }

}
