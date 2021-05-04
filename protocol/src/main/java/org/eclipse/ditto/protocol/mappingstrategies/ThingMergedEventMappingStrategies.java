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

import java.time.Instant;
import java.util.HashMap;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for merged thing events.
 */
final class ThingMergedEventMappingStrategies extends AbstractThingMappingStrategies<ThingMerged> {

    private static final ThingMergedEventMappingStrategies INSTANCE = new ThingMergedEventMappingStrategies();

    private ThingMergedEventMappingStrategies() {
        super(new HashMap<>());
    }

    static ThingMergedEventMappingStrategies getInstance() {
        return INSTANCE;
    }

    @Override
    public JsonifiableMapper<ThingMerged> find(final String type) {
        return ThingMergedEventMappingStrategies::thingMerged;
    }

    private static ThingMerged thingMerged(final Adaptable adaptable) {
        return ThingMerged.of(thingIdFrom(adaptable), JsonPointer.of(adaptable.getPayload().getPath().toString()),
                adaptable.getPayload().getValue().orElse(null), revisionFrom(adaptable), timestampFrom(adaptable),
                dittoHeadersFrom(adaptable), metadataFrom(adaptable));
    }

    private static long revisionFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getRevision().orElseThrow(() -> JsonMissingFieldException.newBuilder()
                .fieldName(Payload.JsonFields.REVISION.getPointer().toString()).build());
    }

    @Nullable
    private static Instant timestampFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getTimestamp().orElse(null);
    }

    @Nullable
    private static Metadata metadataFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getMetadata().orElse(null);
    }

}
