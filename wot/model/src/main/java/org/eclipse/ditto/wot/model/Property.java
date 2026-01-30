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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A Property is an {@link Interaction} describing how state of a Thing is exposed.
 * <p>
 * "This state can then be retrieved (read) and optionally updated (write). Things can also choose to make Properties
 * observable by pushing the new state after a change."
 * </p>
 * <p>
 * Properties combine the characteristics of an {@link Interaction} with a {@link SingleDataSchema} that describes
 * the data type of the property value.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#propertyaffordance">WoT TD PropertyAffordance</a>
 * @since 2.4.0
 */
public interface Property extends SingleDataSchema, Interaction<Property, PropertyFormElement, PropertyForms> {

    /**
     * Creates a new Property from the specified JSON object.
     *
     * @param propertyName the name of the property (the key in the properties map).
     * @param jsonObject the JSON object representing the property affordance.
     * @return the Property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Property fromJson(final CharSequence propertyName, final JsonObject jsonObject) {
        return new ImmutableProperty(checkNotNull(propertyName, "propertyName").toString(), jsonObject);
    }

    /**
     * Creates a new builder for building a Property.
     *
     * @param propertyName the name of the property.
     * @return the builder.
     * @throws NullPointerException if {@code propertyName} is {@code null}.
     */
    static Property.Builder newBuilder(final CharSequence propertyName) {
        return Property.Builder.newBuilder(propertyName);
    }

    /**
     * Creates a new builder for building a Property, initialized with the values from the specified JSON object.
     *
     * @param propertyName the name of the property.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Property.Builder newBuilder(final CharSequence propertyName, final JsonObject jsonObject) {
        return Property.Builder.newBuilder(propertyName, jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a Property, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    @Override
    default Property.Builder toBuilder() {
        return Property.Builder.newBuilder(getPropertyName(), toJson());
    }

    /**
     * Returns the name of this property as defined in the Thing Description's properties map.
     *
     * @return the property name.
     */
    String getPropertyName();

    /**
     * Returns whether this property is observable, meaning the Thing will push notifications when the property
     * value changes.
     *
     * @return {@code true} if the property is observable, {@code false} otherwise.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#propertyaffordance">WoT TD PropertyAffordance (observable)</a>
     */
    boolean isObservable();

    /**
     * Returns whether this property's data schema is a {@link BooleanSchema}.
     *
     * @return {@code true} if the schema type is boolean.
     */
    boolean isBooleanSchema();

    /**
     * Returns this property's data schema as a {@link BooleanSchema}.
     *
     * @return the BooleanSchema.
     * @throws ClassCastException if this property's schema is not a BooleanSchema.
     */
    BooleanSchema asBooleanSchema();

    /**
     * Returns whether this property's data schema is an {@link IntegerSchema}.
     *
     * @return {@code true} if the schema type is integer.
     */
    boolean isIntegerSchema();

    /**
     * Returns this property's data schema as an {@link IntegerSchema}.
     *
     * @return the IntegerSchema.
     * @throws ClassCastException if this property's schema is not an IntegerSchema.
     */
    IntegerSchema asIntegerSchema();

    /**
     * Returns whether this property's data schema is a {@link NumberSchema}.
     *
     * @return {@code true} if the schema type is number.
     */
    boolean isNumberSchema();

    /**
     * Returns this property's data schema as a {@link NumberSchema}.
     *
     * @return the NumberSchema.
     * @throws ClassCastException if this property's schema is not a NumberSchema.
     */
    NumberSchema asNumberSchema();

    /**
     * Returns whether this property's data schema is a {@link StringSchema}.
     *
     * @return {@code true} if the schema type is string.
     */
    boolean isStringSchema();

    /**
     * Returns this property's data schema as a {@link StringSchema}.
     *
     * @return the StringSchema.
     * @throws ClassCastException if this property's schema is not a StringSchema.
     */
    StringSchema asStringSchema();

    /**
     * Returns whether this property's data schema is an {@link ObjectSchema}.
     *
     * @return {@code true} if the schema type is object.
     */
    boolean isObjectSchema();

    /**
     * Returns this property's data schema as an {@link ObjectSchema}.
     *
     * @return the ObjectSchema.
     * @throws ClassCastException if this property's schema is not an ObjectSchema.
     */
    ObjectSchema asObjectSchema();

    /**
     * Returns whether this property's data schema is an {@link ArraySchema}.
     *
     * @return {@code true} if the schema type is array.
     */
    boolean isArraySchema();

    /**
     * Returns this property's data schema as an {@link ArraySchema}.
     *
     * @return the ArraySchema.
     * @throws ClassCastException if this property's schema is not an ArraySchema.
     */
    ArraySchema asArraySchema();

    /**
     * Returns whether this property's data schema is a {@link NullSchema}.
     *
     * @return {@code true} if the schema type is null.
     */
    boolean isNullSchema();

    /**
     * Returns this property's data schema as a {@link NullSchema}.
     *
     * @return the NullSchema.
     * @throws ClassCastException if this property's schema is not a NullSchema.
     */
    NullSchema asNullSchema();

    /**
     * A mutable builder with a fluent API for building a {@link Property}.
     */
    interface Builder extends Interaction.Builder<Builder, Property, PropertyFormElement, PropertyForms>,
            SingleDataSchema.Builder<Builder, Property> {

        /**
         * Creates a new builder for building a Property.
         *
         * @param propertyName the name of the property.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence propertyName) {
            return new MutablePropertyBuilder(checkNotNull(propertyName, "propertyName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a Property, initialized with the values from the specified JSON object.
         *
         * @param propertyName the name of the property.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence propertyName, final JsonObject jsonObject) {
            return new MutablePropertyBuilder(checkNotNull(propertyName, "propertyName").toString(), jsonObject.toBuilder());
        }

        /**
         * Sets whether this property is observable.
         *
         * @param observable whether the property is observable, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#propertyaffordance">WoT TD PropertyAffordance (observable)</a>
         */
        Builder setObservable(@Nullable Boolean observable);

        /**
         * Sets the data schema for this property.
         *
         * @param schema the data schema, or {@code null} to remove.
         * @return this builder.
         */
        Builder setSchema(@Nullable SingleDataSchema schema);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a Property.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the observable flag.
         */
        public static final JsonFieldDefinition<Boolean> OBSERVABLE = JsonFactory.newBooleanFieldDefinition(
                "observable");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
