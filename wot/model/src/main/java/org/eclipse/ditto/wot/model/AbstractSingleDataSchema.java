/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract implementation of {@link SingleDataSchema}.
 */
abstract class AbstractSingleDataSchema implements SingleDataSchema {

    private static final boolean WRITE_ONLY_DEFAULT = false;
    private static final boolean READ_ONLY_DEFAULT = false;

    protected final JsonObject wrappedObject;

    protected AbstractSingleDataSchema(final JsonObject wrappedObject) {
        this.wrappedObject = checkNotNull(wrappedObject, "wrappedObject");
    }

    @Override
    public JsonObject toJson() {
        return wrappedObject;
    }

    @Override
    public Optional<AtType> getAtType() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, DataSchemaJsonFields.AT_TYPE_MULTIPLE)
                        .map(MultipleAtType::fromJson)
                        .map(AtType.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(DataSchemaJsonFields.AT_TYPE)
                                .map(SingleAtType::of)
                                .orElse(null))
        );
    }

    @Override
    public Optional<Description> getDescription() {
        return wrappedObject.getValue(DataSchemaJsonFields.DESCRIPTION)
                .map(Description::of);
    }

    @Override
    public Optional<Descriptions> getDescriptions() {
        return wrappedObject.getValue(DataSchemaJsonFields.DESCRIPTIONS)
                .map(Descriptions::fromJson);
    }

    @Override
    public Optional<Title> getTitle() {
        return wrappedObject.getValue(DataSchemaJsonFields.TITLE)
                .map(Title::of);
    }

    @Override
    public Optional<Titles> getTitles() {
        return wrappedObject.getValue(DataSchemaJsonFields.TITLES)
                .map(Titles::fromJson);
    }

    @Override
    public boolean isWriteOnly() {
        return wrappedObject.getValue(DataSchemaJsonFields.WRITE_ONLY).orElse(WRITE_ONLY_DEFAULT);
    }

    @Override
    public boolean isReadOnly() {
        return wrappedObject.getValue(DataSchemaJsonFields.READ_ONLY).orElse(READ_ONLY_DEFAULT);
    }

    @Override
    public List<SingleDataSchema> getOneOf() {
        return wrappedObject.getValue(DataSchemaJsonFields.ONE_OF)
                .map(array -> array.stream()
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .map(SingleDataSchema::fromJson)
                        .collect(Collectors.toList())
                ).orElse(Collections.emptyList());
    }

    @Override
    public Optional<String> getUnit() {
        return wrappedObject.getValue(DataSchemaJsonFields.UNIT);
    }

    @Override
    public Set<JsonValue> getEnum() {
        return wrappedObject.getValue(DataSchemaJsonFields.ENUM)
                .map(JsonArray::stream)
                .map(stream -> stream.collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    @Override
    public Optional<String> getFormat() {
        return wrappedObject.getValue(DataSchemaJsonFields.FORMAT);
    }

    @Override
    public Optional<JsonValue> getConst() {
        return wrappedObject.getValue(DataSchemaJsonFields.CONST);
    }

    @Override
    public Optional<JsonValue> getDefault() {
        return wrappedObject.getValue(DataSchemaJsonFields.DEFAULT);
    }

    @Override
    public Optional<DataSchemaType> getType() {
        return wrappedObject.getValue(DataSchemaJsonFields.TYPE)
                .flatMap(DataSchemaType::forName)
                .map(DataSchemaType.class::cast);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractSingleDataSchema that = (AbstractSingleDataSchema) o;
        return canEqual(that) && Objects.equals(wrappedObject, that.wrappedObject);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractSingleDataSchema;
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrappedObject);
    }

    @Override
    public String toString() {
        return "wrappedObject=" + wrappedObject;
    }

}
