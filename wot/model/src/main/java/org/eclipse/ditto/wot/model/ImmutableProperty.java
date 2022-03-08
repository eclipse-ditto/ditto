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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link Property}.
 */
@Immutable
final class ImmutableProperty extends AbstractSingleDataSchema implements Property {

    private static final boolean OBSERVABLE_DEFAULT = false;

    private final String propertyName;

    ImmutableProperty(final String propertyName, final JsonObject wrappedObject) {
        super(wrappedObject);
        this.propertyName = propertyName;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public boolean isObservable() {
        return wrappedObject.getValue(JsonFields.OBSERVABLE).orElse(OBSERVABLE_DEFAULT);
    }

    @Override
    public boolean isBooleanSchema() {
        return getType().filter(DataSchemaType.BOOLEAN::equals).isPresent();
    }

    @Override
    public BooleanSchema asBooleanSchema() {
        return BooleanSchema.fromJson(toJson());
    }

    @Override
    public boolean isIntegerSchema() {
        return getType().filter(DataSchemaType.INTEGER::equals).isPresent();
    }

    @Override
    public IntegerSchema asIntegerSchema() {
        return IntegerSchema.fromJson(toJson());
    }

    @Override
    public boolean isNumberSchema() {
        return getType().filter(DataSchemaType.NUMBER::equals).isPresent();
    }

    @Override
    public NumberSchema asNumberSchema() {
        return NumberSchema.fromJson(toJson());
    }

    @Override
    public boolean isStringSchema() {
        return getType().filter(DataSchemaType.STRING::equals).isPresent();
    }

    @Override
    public StringSchema asStringSchema() {
        return StringSchema.fromJson(toJson());
    }

    @Override
    public boolean isObjectSchema() {
        return getType().filter(DataSchemaType.OBJECT::equals).isPresent();
    }

    @Override
    public ObjectSchema asObjectSchema() {
        return ObjectSchema.fromJson(toJson());
    }

    @Override
    public boolean isArraySchema() {
        return getType().filter(DataSchemaType.ARRAY::equals).isPresent();
    }

    @Override
    public ArraySchema asArraySchema() {
        return ArraySchema.fromJson(toJson());
    }

    @Override
    public boolean isNullSchema() {
        return getType().filter(DataSchemaType.NULL::equals).isPresent();
    }

    @Override
    public NullSchema asNullSchema() {
        return NullSchema.fromJson(toJson());
    }

    @Override
    public Optional<PropertyForms> getForms() {
        return wrappedObject.getValue(InteractionJsonFields.FORMS)
                .map(PropertyForms::fromJson);
    }

    @Override
    public Optional<UriVariables> getUriVariables() {
        return wrappedObject.getValue(InteractionJsonFields.URI_VARIABLES)
                .map(UriVariables::fromJson);
    }

    @Override
    public JsonObject getWrappedObject() {
        return wrappedObject;
    }

    @Override
    public Property determineResult(final Supplier<JsonObject> newWrappedSupplier) {
        final JsonObject newWrapped = newWrappedSupplier.get();
        if (!newWrapped.equals(wrappedObject)) {
            return new ImmutableProperty(propertyName, newWrapped);
        }
        return this;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ImmutableProperty that = (ImmutableProperty) o;
        return Objects.equals(propertyName, that.propertyName);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableProperty;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), propertyName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "propertyName=" + propertyName + ", " +
                super.toString() +
                "]";
    }

}
