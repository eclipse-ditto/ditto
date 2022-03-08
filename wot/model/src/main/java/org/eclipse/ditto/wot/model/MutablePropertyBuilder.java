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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Mutable builder for {@link Property}s.
 */
final class MutablePropertyBuilder
        extends AbstractInteractionBuilder<Property.Builder, Property, PropertyFormElement, PropertyForms>
        implements Property.Builder {

    private final String propertyName;

    MutablePropertyBuilder(final String propertyName, final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutablePropertyBuilder.class);
        this.propertyName = propertyName;
    }

    @Override
    public Property.Builder setObservable(@Nullable final Boolean observable) {
        if (observable != null) {
            putValue(Property.JsonFields.OBSERVABLE, observable);
        } else {
            remove(Property.JsonFields.OBSERVABLE);
        }
        return myself;
    }

    @Override
    public Property.Builder setSchema(@Nullable final SingleDataSchema singleDataSchema) {
        if (singleDataSchema != null) {
            wrappedObjectBuilder.setAll(singleDataSchema.toJson());
        }
        return myself;
    }

    @Override
    public Property.Builder setWriteOnly(@Nullable final Boolean writeOnly) {
        if (writeOnly != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.WRITE_ONLY, writeOnly);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.WRITE_ONLY);
        }
        return myself;
    }

    @Override
    public Property.Builder setReadOnly(@Nullable final Boolean readOnly) {
        if (readOnly != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.READ_ONLY, readOnly);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.READ_ONLY);
        }
        return myself;
    }

    @Override
    public Property.Builder setOneOf(@Nullable final Collection<SingleDataSchema> oneOf) {
        if (oneOf != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.ONE_OF, oneOf.stream()
                    .map(SingleDataSchema::toJson)
                    .collect(JsonCollectors.valuesToArray()));
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.ONE_OF);
        }
        return myself;
    }

    @Override
    public Property.Builder setUnit(@Nullable final String unit) {
        putValue(SingleDataSchema.DataSchemaJsonFields.UNIT, unit);
        return myself;
    }

    @Override
    public Property.Builder setEnum(@Nullable final Collection<JsonValue> enumValues) {
        if (enumValues != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.ENUM, enumValues.stream()
                    .collect(JsonCollectors.valuesToArray()));
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.ENUM);
        }
        return myself;
    }

    @Override
    public Property.Builder setFormat(@Nullable final String format) {
        putValue(SingleDataSchema.DataSchemaJsonFields.FORMAT, format);
        return myself;
    }

    @Override
    public Property.Builder setConst(@Nullable final JsonValue constValue) {
        if (constValue != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.CONST, constValue);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.CONST);
        }
        return myself;
    }

    @Override
    public Property.Builder setDefault(@Nullable final JsonValue defaultValue) {
        if (defaultValue != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.DEFAULT, defaultValue);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.DEFAULT);
        }
        return myself;
    }

    @Override
    public Property.Builder setType(@Nullable final DataSchemaType type) {
        if (type != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.TYPE, type.toString());
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.TYPE);
        }
        return myself;
    }

    @Override
    public Property build() {
        return new ImmutableProperty(propertyName, wrappedObjectBuilder.build());
    }

}
