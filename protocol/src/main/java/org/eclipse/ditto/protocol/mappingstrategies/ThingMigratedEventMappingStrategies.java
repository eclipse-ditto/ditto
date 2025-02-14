/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.things.model.signals.events.ThingMigrated;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for migrated thing events.
 */
final class ThingMigratedEventMappingStrategies extends AbstractThingMappingStrategies<ThingMigrated> {

    private static final ThingMigratedEventMappingStrategies INSTANCE = new ThingMigratedEventMappingStrategies();

    private ThingMigratedEventMappingStrategies() {
        super(new HashMap<>());
    }

    static ThingMigratedEventMappingStrategies getInstance() {
        return INSTANCE;
    }

    @Override
    public JsonifiableMapper<ThingMigrated> find(final String type) {
        return ThingMigratedEventMappingStrategies::thingMigrated;
    }

    private static ThingMigrated thingMigrated(final Adaptable adaptable) {
        return ThingMigrated.of(
                thingFrom(adaptable),
                revisionFrom(adaptable),
                timestampFrom(adaptable),
                dittoHeadersFrom(adaptable),
                metadataFrom(adaptable)
        );
    }

    private static long revisionFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getRevision()
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
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
