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
 * "This state can then be retrieved (read) and optionally updated (write). Things can also choose to make Properties
 * observable by pushing the new state after a change."
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#propertyaffordance">WoT TD PropertyAffordance</a>
 * @since 2.4.0
 */
public interface Property extends SingleDataSchema, Interaction<Property, PropertyFormElement, PropertyForms> {

    static Property fromJson(final CharSequence propertyName, final JsonObject jsonObject) {
        return new ImmutableProperty(checkNotNull(propertyName, "propertyName").toString(), jsonObject);
    }

    static Property.Builder newBuilder(final CharSequence propertyName) {
        return Property.Builder.newBuilder(propertyName);
    }

    static Property.Builder newBuilder(final CharSequence propertyName, final JsonObject jsonObject) {
        return Property.Builder.newBuilder(propertyName, jsonObject);
    }

    @Override
    default Property.Builder toBuilder() {
        return Property.Builder.newBuilder(getPropertyName(), toJson());
    }

    String getPropertyName();

    boolean isObservable();

    boolean isBooleanSchema();

    BooleanSchema asBooleanSchema();

    boolean isIntegerSchema();

    IntegerSchema asIntegerSchema();

    boolean isNumberSchema();

    NumberSchema asNumberSchema();

    boolean isStringSchema();

    StringSchema asStringSchema();

    boolean isObjectSchema();

    ObjectSchema asObjectSchema();

    boolean isArraySchema();

    ArraySchema asArraySchema();

    boolean isNullSchema();

    NullSchema asNullSchema();

    interface Builder extends Interaction.Builder<Builder, Property, PropertyFormElement, PropertyForms>,
            SingleDataSchema.Builder<Builder, Property> {

        static Builder newBuilder(final CharSequence propertyName) {
            return new MutablePropertyBuilder(checkNotNull(propertyName, "propertyName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence propertyName, final JsonObject jsonObject) {
            return new MutablePropertyBuilder(checkNotNull(propertyName, "propertyName").toString(), jsonObject.toBuilder());
        }

        Builder setObservable(@Nullable Boolean observable);

        Builder setSchema(@Nullable SingleDataSchema schema);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a Property.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<Boolean> OBSERVABLE = JsonFactory.newBooleanFieldDefinition(
                "observable");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
