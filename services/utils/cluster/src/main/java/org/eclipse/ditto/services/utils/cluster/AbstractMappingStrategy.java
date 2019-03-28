/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;

@Immutable
public abstract class AbstractMappingStrategy implements MappingStrategy {

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final HashMap<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> strategies = new HashMap<>();
        strategies.putAll(getGlobalStrategies());
        strategies.putAll(getIndividualStrategies());
        return strategies;
    }

    private Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> getGlobalStrategies() {
        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        builder.add(GlobalErrorRegistry.getInstance())
                .add(GlobalCommandRegistry.getInstance())
                .add(GlobalCommandResponseRegistry.getInstance())
                .add(GlobalEventRegistry.getInstance());

        return builder.build();
    }

    protected abstract Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> getIndividualStrategies();
}
