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
package org.eclipse.ditto.services.utils.persistentactors.events;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.entity.metadata.MetadataBuilder;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Creates or extends/modifies Metadata of an entity based on {@link MetadataHeader}s of an Event's DittoHeaders.
 * See <a href="https://github.com/eclipse/ditto/issues/680#issuecomment-654165747">Github issue #680.</a>.
 *
 * @since 1.2.0
 */
@Immutable
public final class MetadataFromEvent implements Supplier<Metadata> {

    private final Event<?> event;
    @Nullable private final Metadata existingMetadata;

    private MetadataFromEvent(final Event<?> event, @Nullable final Metadata existingMetadata) {
        this.event = event;
        this.existingMetadata = existingMetadata;
    }

    /**
     * Returns an instance of {@code MetadataFromEvent}.
     *
     * @param event provides modified paths and headers.
     * @param entity provides existing metadata to be extended.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MetadataFromEvent of(final Event<?> event, final Entity<?> entity) {
        return new MetadataFromEvent(checkNotNull(event, "event"), getMetadataOrNull(checkNotNull(entity, "entity")));
    }

    @Nullable
    private static Metadata getMetadataOrNull(final Entity<?> entity) {
        return entity.getMetadata().orElse(null);
    }

    /**
     * Builds Metadata based on the Event and the existing metadata of this instance.
     * If the existing metadata is {@code null} and the event does not contain an entity for its implemented schema
     * version, the result of this method is {@code null}.
     * Otherwise either a new Metadata is created or the existing metadata gets modified and/or extended.
     * The values of the returned metadata are obtained from the metadata headers within the DittoHeaders of this
     * instance's event.
     *
     * @return the created, modified or extended Metadata or {@code null}.
     * @throws IllegalArgumentException if the key of a metadata header
     * <ul>
     *     <li>is empty,</li>
     *     <li>starts with an asterisk ({@code *}) and has not exactly two levels,</li>
     *     <li>contains an asterisk at any level but the first.</li>
     * </ul>
     * @throws org.eclipse.ditto.json.JsonParseException if the value of a metadata header cannot be parsed.
     */
    @Override
    @Nullable
    public Metadata get() {
        final Metadata result;
        final Optional<JsonValue> entityOptional = event.getEntity(event.getImplementedSchemaVersion());
        if (entityOptional.isPresent()) {
            final List<MetadataHeader> metadataHeaders = getMetadataHeaders();
            if (metadataHeaders.isEmpty()) {
                result = existingMetadata;
            } else {
                result = buildMetadata(entityOptional.get(), metadataHeaders);
            }
        } else {
            result = existingMetadata;
        }
        return result;
    }

    private List<MetadataHeader> getMetadataHeaders() {
        final MetadataHeaderParser metadataHeaderParser = MetadataHeaderParser.getInstance();
        return metadataHeaderParser.parse(event.getDittoHeaders());
    }

    private Metadata buildMetadata(final JsonValue entity, final List<MetadataHeader> metadataHeaders) {
        final MetadataBuilder metadataBuilder = getMetadataBuilder();

        final Consumer<MetadataHeader> addMetadataToBuilder = metadataHeader -> {
            final MetadataHeaderKey metadataHeaderKey = metadataHeader.getKey();
            final JsonPointer resourcePath = event.getResourcePath();
            if (metadataHeaderKey.appliesToAllLeaves()) {
                addMetadataToLeaf(resourcePath, metadataHeader, metadataBuilder, entity);
            } else {
                metadataBuilder.set(resourcePath.append(metadataHeaderKey.getPath()), metadataHeader.getValue());
            }
        };

        // Sorting ensures that values for a key with specific path overwrite overlapping values for a key with wildcard
        // path, i. e. specific > generic.
        Collections.sort(metadataHeaders);
        metadataHeaders.forEach(addMetadataToBuilder);

        return metadataBuilder.build();
    }

    private MetadataBuilder getMetadataBuilder() {
        return null != existingMetadata ? existingMetadata.toBuilder() : Metadata.newBuilder();
    }

    private static void addMetadataToLeaf(final JsonPointer path,
            final MetadataHeader metadataHeader,
            final MetadataBuilder metadataBuilder,
            final JsonValue entity) {

        if (entity.isObject()) {
            final JsonObject entityObject = entity.asObject();
            entityObject.forEach(jsonField -> {
                final JsonKey key = jsonField.getKey();
                addMetadataToLeaf(path.append(key.asPointer()), metadataHeader, metadataBuilder, jsonField.getValue());
            });
        } else {
            final MetadataHeaderKey metadataHeaderKey = metadataHeader.getKey();
            metadataBuilder.set(path.append(metadataHeaderKey.getPath()), metadataHeader.getValue());
        }
    }

}
