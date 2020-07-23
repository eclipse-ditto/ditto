/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Metadata;
import org.eclipse.ditto.model.things.MetadataBuilder;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import java.time.Instant;
import java.util.Optional;


/**
 * Creates or extends a Metadata Object based on a Ditto Event.
 */
public class ThingMetadataFactory {

    public static final String ISSUED_AT = "issuedAt";
    public static final String DITTO_HEADER_METADATA_PREFIX = "ditto-metadata:";
    public static final String ISSUED_AT_KEY = DITTO_HEADER_METADATA_PREFIX + ISSUED_AT;

    private ThingMetadataFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Builds a suitable Metadata object based on the Metadata from thing and according to the
     * given event.
     *
     * Uses {@code Instant.now()} as value for issuedAt if none is given in header.
     *
     * @param event event to get modified paths and headers from
     * @param thing thing to get current metadata from
     * @param <T> Event Type
     * @return Metadata element
     */
    public static <T extends ThingEvent<T>> Metadata buildFromEvent(T event, Thing thing) {
        return buildFromEvent(event, thing, Instant.now().toString());
    }

    /**
     * Builds a suitable Metadata object based on the Metadata from thing and according to the
     * given event
     * @param event event to get modified paths and headers from
     * @param thing thing to get current metadata from
     * @param issuedAt Value to use for issuedAt metadata if none is given in the header
     * @param <T> Event Type
     * @return Metadata element
     */
    public static <T extends ThingEvent<T>> Metadata buildFromEvent(T event, Thing thing, String issuedAt) {
        // Create Metadata from Header, see https://github.com/eclipse/ditto/issues/680#issuecomment-654165747
        // Use existing Metadata or create a new Builder if none exists
        org.eclipse.ditto.model.things.MetadataBuilder metadataBuilder = thing.getMetadata()
            .map(Metadata::toBuilder)
            .orElse(Metadata.newBuilder());

        // First, check issued at
        if (event.getEntity(event.getImplementedSchemaVersion()).isEmpty()) {
            return metadataBuilder.build();
        }

        JsonValue schema = event.getEntity(event.getImplementedSchemaVersion()).get();

        String issuedAtValue = getIssuedAt(event.getDittoHeaders()).orElse(issuedAt);

        addMetadataToLeaf(event.getResourcePath(), metadataBuilder, schema, issuedAtValue);

        // Add further metadata, if it is requested explicitly
        for (String headerKey : event.getDittoHeaders().keySet()) {
            // Ignore header settings
            if (ISSUED_AT_KEY.equals(headerKey)) {
                continue;
            }
            if (headerKey.startsWith(DITTO_HEADER_METADATA_PREFIX)) {
                String metadataKey = headerKey.substring(DITTO_HEADER_METADATA_PREFIX.length());
                String metadataValue = event.getDittoHeaders().get(headerKey);
                metadataBuilder.set(JsonPointer.of(metadataKey), metadataValue);
            }
        }

        return metadataBuilder.build();
    }

    private static void addMetadataToLeaf(JsonPointer path, MetadataBuilder metadataBuilder, JsonValue schema, String issuedAtValue) {
        if (schema.isObject()) {
            for (JsonKey key : schema.asObject().getKeys()) {
                addMetadataToLeaf(path.append(key.asPointer()), metadataBuilder, schema.asObject().getValue(key.asPointer()).get(), issuedAtValue);
                // metadataBuilder.set(.append(JsonPointer.of(ISSUED_AT)), issuedAtValue);
            }
        } else {
            metadataBuilder.set(path.append(JsonPointer.of(ISSUED_AT)), issuedAtValue);
        }
    }

    private static Optional<String> getIssuedAt(DittoHeaders headers) {
        return Optional.ofNullable(headers.get(ISSUED_AT_KEY));
    }
}
