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
package org.eclipse.ditto.internal.utils.persistentactors;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Creates or extends/modifies Metadata of an entity based on {@link MetadataHeader}s of a Signal's DittoHeaders.
 *
 * @since 1.3.0
 */
@Immutable
public final class MetadataFromSignal implements Supplier<Metadata> {

    private final Signal<?> signal;
    private final WithOptionalEntity withOptionalEntity;
    @Nullable private final Metadata existingMetadata;

    private MetadataFromSignal(final Signal<?> signal, final WithOptionalEntity withOptionalEntity,
            @Nullable final Metadata existingMetadata) {
        this.signal = signal;
        this.withOptionalEntity = withOptionalEntity;
        this.existingMetadata = existingMetadata;
    }

    /**
     * Returns an instance of {@code MetadataFromSignal}.
     *
     * @param signal provides modified paths and headers.
     * @param withOptionalEntity provides the optional entity used to check which paths/leaves were actually modified.
     * @param existingMetadata provides existing metadata to be extended.
     * @return the instance.
     * @throws NullPointerException if {@code signal} is {@code null}.
     */
    public static MetadataFromSignal of(final Signal<?> signal,
            final WithOptionalEntity withOptionalEntity,
            @Nullable final Metadata existingMetadata) {
        return new MetadataFromSignal(checkNotNull(signal, "signal"),
                checkNotNull(withOptionalEntity, "withOptionalEntity"),
                existingMetadata);
    }

    /**
     * Builds Metadata based on the Command and the existing metadata of this instance.
     * If the existing metadata is {@code null} and the command does not contain an entity for its implemented schema
     * version, the result of this method is {@code null}.
     * Otherwise, either a new Metadata is created or the existing metadata gets modified and/or extended.
     * The values of the returned metadata are obtained from the metadata headers within the DittoHeaders of this
     * instance's command.
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
        final Optional<JsonValue> entityOptional = withOptionalEntity.getEntity(
                signal.getDittoHeaders().getSchemaVersion().orElse(signal.getLatestSchemaVersion())
        );
        if (entityOptional.isPresent()) {
            final SortedSet<MetadataHeader> metadataHeaders = getMetadataHeadersToPut();
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

    private SortedSet<MetadataHeader> getMetadataHeadersToPut() {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        return dittoHeaders.getMetadataHeadersToPut();
    }

    private Metadata buildMetadata(final JsonValue entity, final SortedSet<MetadataHeader> metadataHeaders) {
        final MetadataBuilder metadataBuilder = getMetadataBuilder();

        final Consumer<MetadataHeader> addMetadataToBuilder = metadataHeader -> {
            final MetadataHeaderKey metadataHeaderKey = metadataHeader.getKey();
            final JsonValue metadataHeaderValue = metadataHeader.getValue();
            if (entity.isObject()) {
                final Optional<JsonField> field = entity.asObject().getField(metadataHeaderKey.getPath());
                if (field.isPresent() && !metadataHeaderValue.isObject()) {
                    // ignore metadata header value that isn't an object.
                    return;
                }
            }
            final JsonPointer attachedPropertyPath = metadataHeaderKey.getPath().cutLeaf();
            if (metadataHeaderKey.appliesToAllLeaves()) {
                addMetadataToLeaf(JsonPointer.empty(), metadataHeader, metadataBuilder, entity);
            } else if (entity.isObject() && entity.asObject().getField(attachedPropertyPath).isPresent()) {
                metadataBuilder.set(metadataHeaderKey.getPath(), metadataHeaderValue);
            } else if (attachedPropertyPath.isEmpty()) {
                metadataBuilder.set(metadataHeaderKey.getPath(), metadataHeaderValue);
            }
        };

        metadataHeaders.forEach(addMetadataToBuilder);

        return metadataBuilder.build();
    }

    private MetadataBuilder getMetadataBuilder() {
        return null != existingMetadata ? Metadata.newBuilder().setAll(existingMetadata) : Metadata.newBuilder();
    }

    private static void addMetadataToLeaf(final JsonPointer path,
            final MetadataHeader metadataHeader,
            final MetadataBuilder metadataBuilder,
            final JsonValue entity) {

        if (entity.isObject()) {
            final JsonObject entityObject = entity.asObject();
            entityObject.stream()
                    .filter(field -> !(field.isMarkedAs(FieldType.SPECIAL) || field.isMarkedAs(FieldType.HIDDEN)))
                    .forEach(jsonField -> {
                        final JsonKey key = jsonField.getKey();
                        addMetadataToLeaf(path.append(key.asPointer()), metadataHeader, metadataBuilder,
                                jsonField.getValue());
                    });
        } else {
            final MetadataHeaderKey metadataHeaderKey = metadataHeader.getKey();
            metadataBuilder.set(path.append(metadataHeaderKey.getPath()), metadataHeader.getValue());
        }
    }

}
