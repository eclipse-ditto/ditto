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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * A SingleDataSchema describes a used data format which can be used for validation.
 * It has the following subclasses:
 * <ul>
 *     <li>{@link ArraySchema}</li>
 *     <li>{@link BooleanSchema}</li>
 *     <li>{@link NumberSchema}</li>
 *     <li>{@link IntegerSchema}</li>
 *     <li>{@link ObjectSchema}</li>
 *     <li>{@link StringSchema}</li>
 *     <li>{@link NullSchema}</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema</a>
 * @since 2.4.0
 */
public interface SingleDataSchema extends DataSchema, Jsonifiable<JsonObject> {

    static SingleDataSchema fromJson(final JsonObject jsonObject) {
        return jsonObject.getValue(DataSchemaJsonFields.TYPE)
                .flatMap(DataSchemaType::forName)
                .map(type -> {
                    switch (type) {
                        case BOOLEAN:
                            return BooleanSchema.fromJson(jsonObject);
                        case INTEGER:
                            return IntegerSchema.fromJson(jsonObject);
                        case NUMBER:
                            return NumberSchema.fromJson(jsonObject);
                        case STRING:
                            return StringSchema.fromJson(jsonObject);
                        case OBJECT:
                            return ObjectSchema.fromJson(jsonObject);
                        case ARRAY:
                            return ArraySchema.fromJson(jsonObject);
                        case NULL:
                            return NullSchema.fromJson(jsonObject);
                        default:
                            throw new IllegalArgumentException("Unsupported dataSchema-type: " + type);
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("Could not create SingleDataSchema"));
    }

    static BooleanSchema.Builder newBooleanSchemaBuilder() {
        return BooleanSchema.newBuilder();
    }

    static IntegerSchema.Builder newIntegerSchemaBuilder() {
        return IntegerSchema.newBuilder();
    }

    static NumberSchema.Builder newNumberSchemaBuilder() {
        return NumberSchema.newBuilder();
    }

    static StringSchema.Builder newStringSchemaBuilder() {
        return StringSchema.newBuilder();
    }

    static ObjectSchema.Builder newObjectSchemaBuilder() {
        return ObjectSchema.newBuilder();
    }

    static ArraySchema.Builder newArraySchemaBuilder() {
        return ArraySchema.newBuilder();
    }

    static NullSchema.Builder newNullSchemaBuilder() {
        return NullSchema.newBuilder();
    }

    Optional<AtType> getAtType();

    Optional<DataSchemaType> getType();

    Optional<Description> getDescription();

    Optional<Descriptions> getDescriptions();

    Optional<Title> getTitle();

    Optional<Titles> getTitles();

    boolean isWriteOnly();

    boolean isReadOnly();

    List<SingleDataSchema> getOneOf();

    Optional<String> getUnit();

    Set<JsonValue> getEnum();

    Optional<String> getFormat();

    Optional<JsonValue> getConst();

    Optional<JsonValue> getDefault();

    interface Builder<B extends Builder<B, S>, S extends SingleDataSchema> {

        B setAtType(@Nullable AtType atType);

        B setType(@Nullable DataSchemaType type);

        B setTitle(@Nullable Title title);

        B setTitles(@Nullable Titles title);

        B setDescription(@Nullable Description description);

        B setDescriptions(@Nullable Descriptions descriptions);

        B setWriteOnly(@Nullable Boolean writeOnly);

        B setReadOnly(@Nullable Boolean readOnly);

        B setOneOf(@Nullable Collection<SingleDataSchema> oneOf);

        B setUnit(@Nullable String unit);

        B setEnum(@Nullable Collection<JsonValue> enumValues);

        B setFormat(@Nullable String format);

        B setConst(@Nullable JsonValue constValue);

        B setDefault(@Nullable JsonValue defaultValue);

        S build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a SingleDataSchema.
     */
    @Immutable
    final class DataSchemaJsonFields {

        public static final JsonFieldDefinition<String> AT_TYPE = JsonFactory.newStringFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<JsonArray> AT_TYPE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<String> TITLE = JsonFactory.newStringFieldDefinition(
                "title");

        public static final JsonFieldDefinition<JsonObject> TITLES = JsonFactory.newJsonObjectFieldDefinition(
                "titles");

        public static final JsonFieldDefinition<String> DESCRIPTION = JsonFactory.newStringFieldDefinition(
                "description");

        public static final JsonFieldDefinition<JsonObject> DESCRIPTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "descriptions");

        public static final JsonFieldDefinition<Boolean> WRITE_ONLY = JsonFactory.newBooleanFieldDefinition(
                "writeOnly");

        public static final JsonFieldDefinition<Boolean> READ_ONLY = JsonFactory.newBooleanFieldDefinition(
                "readOnly");

        public static final JsonFieldDefinition<JsonArray> ONE_OF = JsonFactory.newJsonArrayFieldDefinition(
                "oneOf");

        public static final JsonFieldDefinition<String> UNIT = JsonFactory.newStringFieldDefinition(
                "unit");

        public static final JsonFieldDefinition<JsonArray> ENUM = JsonFactory.newJsonArrayFieldDefinition(
                "enum");

        public static final JsonFieldDefinition<String> FORMAT = JsonFactory.newStringFieldDefinition(
                "format");

        public static final JsonFieldDefinition<JsonValue> CONST = JsonFactory.newJsonValueFieldDefinition(
                "const");

        public static final JsonFieldDefinition<JsonValue> DEFAULT = JsonFactory.newJsonValueFieldDefinition(
                "default");

        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition(
                "type");

        private DataSchemaJsonFields() {
            throw new AssertionError();
        }
    }
}
