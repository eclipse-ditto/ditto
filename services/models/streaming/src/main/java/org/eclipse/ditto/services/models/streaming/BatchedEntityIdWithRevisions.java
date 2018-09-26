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
package org.eclipse.ditto.services.models.streaming;

import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.WithManifest;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * A list of {@code EntityIdWithRevision} batched together.
 */
@Immutable
public final class BatchedEntityIdWithRevisions<E extends EntityIdWithRevision>
        implements Jsonifiable<JsonObject>, StreamingMessage, WithManifest {

    static final JsonFieldDefinition<String> JSON_TYPE = Command.JsonFields.TYPE;

    static final JsonFieldDefinition<JsonArray> JSON_ELEMENTS =
            JsonFactory.newJsonArrayFieldDefinition("elements", V_1, V_2);

    private final String type;
    private final List<E> elements;

    private BatchedEntityIdWithRevisions(final String type, final List<E> elements) {
        this.type = type;
        this.elements = elements;
    }

    /**
     * Create a {@code BatchedEntityIdWithRevisions} object.
     *
     * @param <T> type of the elements.
     * @param elementClass class of the elements.
     * @param elements the batched elements.
     * @return a new {@code BatchedEntityIdWithRevisions} object.
     */
    public static <T extends EntityIdWithRevision> BatchedEntityIdWithRevisions<T> of(final Class<T> elementClass,
            final List<T> elements) {
        return new BatchedEntityIdWithRevisions<>(typeOf(elementClass), elements);
    }

    /**
     * Create a type name for the JSON deserializer based on the type of elements.
     *
     * @param <T> type of the elements.
     * @param elementClass class of the elements.
     * @return type name of {@code BatchedEntityIdWithRevisions} with elements of type {@code T}.
     */
    public static <T extends EntityIdWithRevision> String typeOf(final Class<T> elementClass) {
        return TYPE_PREFIX + "batched" + elementClass.getSimpleName();
    }

    /**
     * Create a deserializer from a deserializer of the elements.
     *
     * @param <T> type of the elements.
     * @param elementDeserializer deserializer for the elements.
     * @return deserializer for {@code BatchedEntityIdWithRevisions} with elements of type {@code T}.
     */
    public static <T extends EntityIdWithRevision> Function<JsonObject, Jsonifiable<?>> deserializer(
            final Function<JsonObject, T> elementDeserializer) {
        return jsonObject -> {
            final String type = jsonObject.getValueOrThrow(JSON_TYPE);
            final JsonArray jsonArray = jsonObject.getValueOrThrow(JSON_ELEMENTS);
            final List<T> elements = jsonArray.stream()
                    .map(jsonValue -> elementDeserializer.apply(jsonValue.asObject()))
                    .collect(Collectors.toList());
            return new BatchedEntityIdWithRevisions<T>(type, elements);
        };
    }

    /**
     * Retrieve the elements.
     *
     * @return the elements.
     */
    public List<E> getElements() {
        return elements;
    }

    @Override
    public JsonObject toJson() {
        final JsonArray jsonArray = elements.stream().map(Jsonifiable::toJson).collect(JsonCollectors.valuesToArray());
        return JsonFactory.newObjectBuilder()
                .set(JSON_TYPE, type)
                .set(JSON_ELEMENTS, jsonArray)
                .build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, elements);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        } else {
            final BatchedEntityIdWithRevisions<?> that = (BatchedEntityIdWithRevisions<?>) obj;
            return Objects.equals(type, that.type) && Objects.equals(elements, that.elements);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", elements=" + elements
                + "]";
    }

    @Nonnull
    @Override
    public String getManifest() {
        return type;
    }
}
