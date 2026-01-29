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
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * A SingleDataSchema describes a used data format which can be used for validation.
 * <p>
 * It has the following subclasses representing the different JSON data types:
 * </p>
 * <ul>
 *     <li>{@link ArraySchema} - for JSON arrays</li>
 *     <li>{@link BooleanSchema} - for JSON booleans</li>
 *     <li>{@link NumberSchema} - for JSON numbers (floating point)</li>
 *     <li>{@link IntegerSchema} - for JSON integers</li>
 *     <li>{@link ObjectSchema} - for JSON objects</li>
 *     <li>{@link StringSchema} - for JSON strings</li>
 *     <li>{@link NullSchema} - for JSON null values</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema</a>
 * @since 2.4.0
 */
public interface SingleDataSchema extends DataSchema, Jsonifiable<JsonObject> {

    /**
     * Creates a SingleDataSchema from the specified JSON object, automatically determining the correct
     * subtype based on the {@code type} field.
     *
     * @param jsonObject the JSON object representing the data schema.
     * @return the appropriate SingleDataSchema subtype.
     * @throws IllegalArgumentException if the type is not supported.
     */
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
                .orElseGet(() -> new ImmutableDataSchemaWithoutType(jsonObject));
    }

    /**
     * Creates a new builder for building a {@link BooleanSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#booleanschema">WoT TD BooleanSchema</a>
     */
    static BooleanSchema.Builder newBooleanSchemaBuilder() {
        return BooleanSchema.newBuilder();
    }

    /**
     * Creates a new builder for building an {@link IntegerSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema</a>
     */
    static IntegerSchema.Builder newIntegerSchemaBuilder() {
        return IntegerSchema.newBuilder();
    }

    /**
     * Creates a new builder for building a {@link NumberSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema</a>
     */
    static NumberSchema.Builder newNumberSchemaBuilder() {
        return NumberSchema.newBuilder();
    }

    /**
     * Creates a new builder for building a {@link StringSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema</a>
     */
    static StringSchema.Builder newStringSchemaBuilder() {
        return StringSchema.newBuilder();
    }

    /**
     * Creates a new builder for building an {@link ObjectSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#objectschema">WoT TD ObjectSchema</a>
     */
    static ObjectSchema.Builder newObjectSchemaBuilder() {
        return ObjectSchema.newBuilder();
    }

    /**
     * Creates a new builder for building an {@link ArraySchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#arrayschema">WoT TD ArraySchema</a>
     */
    static ArraySchema.Builder newArraySchemaBuilder() {
        return ArraySchema.newBuilder();
    }

    /**
     * Creates a new builder for building a {@link NullSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#nullschema">WoT TD NullSchema</a>
     */
    static NullSchema.Builder newNullSchemaBuilder() {
        return NullSchema.newBuilder();
    }

    /**
     * Returns the optional JSON-LD {@code @type} providing semantic annotations for this data schema.
     *
     * @return the optional semantic type annotation.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (@type)</a>
     */
    Optional<AtType> getAtType();

    /**
     * Returns the optional data type (e.g., boolean, integer, number, string, object, array, null).
     *
     * @return the optional data schema type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (type)</a>
     */
    Optional<DataSchemaType> getType();

    /**
     * Returns the optional human-readable description of this data schema, based on the default language.
     *
     * @return the optional description.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (description)</a>
     */
    Optional<Description> getDescription();

    /**
     * Returns the optional multi-language map of human-readable descriptions for this data schema.
     *
     * @return the optional multi-language descriptions.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD MultiLanguage</a>
     */
    Optional<Descriptions> getDescriptions();

    /**
     * Returns the optional human-readable title of this data schema, based on the default language.
     *
     * @return the optional title.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (title)</a>
     */
    Optional<Title> getTitle();

    /**
     * Returns the optional multi-language map of human-readable titles for this data schema.
     *
     * @return the optional multi-language titles.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD MultiLanguage</a>
     */
    Optional<Titles> getTitles();

    /**
     * Returns whether this data schema is write-only, meaning the data can only be written but not read.
     *
     * @return {@code true} if write-only, {@code false} otherwise.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (writeOnly)</a>
     */
    boolean isWriteOnly();

    /**
     * Returns whether this data schema is read-only, meaning the data can only be read but not written.
     *
     * @return {@code true} if read-only, {@code false} otherwise.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (readOnly)</a>
     */
    boolean isReadOnly();

    /**
     * Returns the list of data schemas where the data must match exactly one of them.
     *
     * @return the list of alternative schemas, may be empty.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (oneOf)</a>
     */
    List<SingleDataSchema> getOneOf();

    /**
     * Returns the optional unit of measurement for the data value (e.g., "celsius", "km/h").
     *
     * @return the optional unit.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (unit)</a>
     */
    Optional<String> getUnit();

    /**
     * Returns the set of restricted values that the data must match.
     *
     * @return the set of enumeration values, may be empty.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (enum)</a>
     */
    Set<JsonValue> getEnum();

    /**
     * Returns the optional format hint for string data (e.g., "date-time", "uri", "email").
     *
     * @return the optional format.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (format)</a>
     */
    Optional<String> getFormat();

    /**
     * Returns the optional constant value that the data must equal.
     *
     * @return the optional constant value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (const)</a>
     */
    Optional<JsonValue> getConst();

    /**
     * Returns the optional default value to use when the data is not provided.
     *
     * @return the optional default value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema (default)</a>
     */
    Optional<JsonValue> getDefault();

    /**
     * A mutable builder for creating {@link SingleDataSchema} instances.
     *
     * @param <B> the type of the Builder.
     * @param <S> the type of the SingleDataSchema.
     */
    interface Builder<B extends Builder<B, S>, S extends SingleDataSchema> {

        /**
         * Sets the JSON-LD {@code @type} for semantic annotations.
         *
         * @param atType the semantic type, or {@code null} to remove.
         * @return this builder.
         */
        B setAtType(@Nullable AtType atType);

        /**
         * Sets the data schema type.
         *
         * @param type the data type, or {@code null} to remove.
         * @return this builder.
         */
        B setType(@Nullable DataSchemaType type);

        /**
         * Sets the human-readable title.
         *
         * @param title the title, or {@code null} to remove.
         * @return this builder.
         */
        B setTitle(@Nullable Title title);

        /**
         * Sets the multi-language titles.
         *
         * @param title the titles map, or {@code null} to remove.
         * @return this builder.
         */
        B setTitles(@Nullable Titles title);

        /**
         * Sets the human-readable description.
         *
         * @param description the description, or {@code null} to remove.
         * @return this builder.
         */
        B setDescription(@Nullable Description description);

        /**
         * Sets the multi-language descriptions.
         *
         * @param descriptions the descriptions map, or {@code null} to remove.
         * @return this builder.
         */
        B setDescriptions(@Nullable Descriptions descriptions);

        /**
         * Sets whether this data schema is write-only.
         *
         * @param writeOnly whether write-only, or {@code null} to remove.
         * @return this builder.
         */
        B setWriteOnly(@Nullable Boolean writeOnly);

        /**
         * Sets whether this data schema is read-only.
         *
         * @param readOnly whether read-only, or {@code null} to remove.
         * @return this builder.
         */
        B setReadOnly(@Nullable Boolean readOnly);

        /**
         * Sets the oneOf schemas for alternative type validation.
         *
         * @param oneOf the collection of alternative schemas, or {@code null} to remove.
         * @return this builder.
         */
        B setOneOf(@Nullable Collection<SingleDataSchema> oneOf);

        /**
         * Sets the unit of measurement.
         *
         * @param unit the unit string, or {@code null} to remove.
         * @return this builder.
         */
        B setUnit(@Nullable String unit);

        /**
         * Sets the enumeration of allowed values.
         *
         * @param enumValues the collection of allowed values, or {@code null} to remove.
         * @return this builder.
         */
        B setEnum(@Nullable Collection<JsonValue> enumValues);

        /**
         * Sets the format hint for string values.
         *
         * @param format the format string, or {@code null} to remove.
         * @return this builder.
         */
        B setFormat(@Nullable String format);

        /**
         * Sets the constant value.
         *
         * @param constValue the constant value, or {@code null} to remove.
         * @return this builder.
         */
        B setConst(@Nullable JsonValue constValue);

        /**
         * Sets the default value.
         *
         * @param defaultValue the default value, or {@code null} to remove.
         * @return this builder.
         */
        B setDefault(@Nullable JsonValue defaultValue);

        /**
         * Provides direct access to the underlying JSON object builder for custom modifications.
         *
         * @param builderConsumer a consumer that receives the JSON object builder.
         * @return this builder.
         */
        B enhanceObjectBuilder(Consumer<JsonObjectBuilder> builderConsumer);

        /**
         * Builds the SingleDataSchema.
         *
         * @return the built SingleDataSchema instance.
         */
        S build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a SingleDataSchema.
     */
    @Immutable
    final class DataSchemaJsonFields {

        /**
         * JSON field definition for the JSON-LD type (single value).
         */
        public static final JsonFieldDefinition<String> AT_TYPE = JsonFactory.newStringFieldDefinition(
                "@type");

        /**
         * JSON field definition for the JSON-LD type (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> AT_TYPE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "@type");

        /**
         * JSON field definition for the title.
         */
        public static final JsonFieldDefinition<String> TITLE = JsonFactory.newStringFieldDefinition(
                "title");

        /**
         * JSON field definition for the multilingual titles.
         */
        public static final JsonFieldDefinition<JsonObject> TITLES = JsonFactory.newJsonObjectFieldDefinition(
                "titles");

        /**
         * JSON field definition for the description.
         */
        public static final JsonFieldDefinition<String> DESCRIPTION = JsonFactory.newStringFieldDefinition(
                "description");

        /**
         * JSON field definition for the multilingual descriptions.
         */
        public static final JsonFieldDefinition<JsonObject> DESCRIPTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "descriptions");

        /**
         * JSON field definition for the write-only flag.
         */
        public static final JsonFieldDefinition<Boolean> WRITE_ONLY = JsonFactory.newBooleanFieldDefinition(
                "writeOnly");

        /**
         * JSON field definition for the read-only flag.
         */
        public static final JsonFieldDefinition<Boolean> READ_ONLY = JsonFactory.newBooleanFieldDefinition(
                "readOnly");

        /**
         * JSON field definition for the oneOf alternative schemas.
         */
        public static final JsonFieldDefinition<JsonArray> ONE_OF = JsonFactory.newJsonArrayFieldDefinition(
                "oneOf");

        /**
         * JSON field definition for the unit of measurement.
         */
        public static final JsonFieldDefinition<String> UNIT = JsonFactory.newStringFieldDefinition(
                "unit");

        /**
         * JSON field definition for the enum values.
         */
        public static final JsonFieldDefinition<JsonArray> ENUM = JsonFactory.newJsonArrayFieldDefinition(
                "enum");

        /**
         * JSON field definition for the format hint.
         */
        public static final JsonFieldDefinition<String> FORMAT = JsonFactory.newStringFieldDefinition(
                "format");

        /**
         * JSON field definition for the constant value.
         */
        public static final JsonFieldDefinition<JsonValue> CONST = JsonFactory.newJsonValueFieldDefinition(
                "const");

        /**
         * JSON field definition for the default value.
         */
        public static final JsonFieldDefinition<JsonValue> DEFAULT = JsonFactory.newJsonValueFieldDefinition(
                "default");

        /**
         * JSON field definition for the data type.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition(
                "type");

        private DataSchemaJsonFields() {
            throw new AssertionError();
        }
    }
}
