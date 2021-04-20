/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.HashMap;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing modify command responses.
 */
final class ThingMergeCommandResponseMappingStrategies
        extends AbstractThingMappingStrategies<MergeThingResponse> {

    private static final ThingMergeCommandResponseMappingStrategies INSTANCE =
            new ThingMergeCommandResponseMappingStrategies();

    private ThingMergeCommandResponseMappingStrategies() {
        super(new HashMap<>());
    }

    @Override
    public JsonifiableMapper<MergeThingResponse> find(final String type) {
        return ThingMergeCommandResponseMappingStrategies::mergeThing;
    }

    static ThingMergeCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static MergeThingResponse mergeThing(final Adaptable adaptable) {
        return MergeThingResponse.of(thingIdFrom(adaptable),
                JsonPointer.of(adaptable.getPayload().getPath().toString()),
                dittoHeadersFrom(adaptable));
    }

}
