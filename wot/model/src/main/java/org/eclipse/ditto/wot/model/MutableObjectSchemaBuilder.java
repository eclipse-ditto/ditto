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
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Mutable builder for {@link ObjectSchema}s.
 */
final class MutableObjectSchemaBuilder
        extends AbstractSingleDataSchemaBuilder<ObjectSchema.Builder, ObjectSchema>
        implements ObjectSchema.Builder {

    MutableObjectSchemaBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableObjectSchemaBuilder.class);
    }

    @Override
    DataSchemaType getDataSchemaType() {
        return DataSchemaType.OBJECT;
    }

    @Override
    public ObjectSchema.Builder setProperties(@Nullable final Map<String, SingleDataSchema> properties) {
        if (properties != null) {
            putValue(ObjectSchema.JsonFields.PROPERTIES, properties.entrySet().stream()
                    .map(entry -> JsonField.newInstance(entry.getKey(), entry.getValue().toJson()))
                    .collect(JsonCollectors.fieldsToObject()));
        } else {
            remove(ObjectSchema.JsonFields.PROPERTIES);
        }
        return myself;
    }

    @Override
    public ObjectSchema.Builder setRequired(@Nullable final Collection<String> required) {
        if (required != null) {
            putValue(ObjectSchema.JsonFields.REQUIRED, required.stream()
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray()));
        } else {
            remove(ObjectSchema.JsonFields.REQUIRED);
        }
        return myself;
    }

    @Override
    public ObjectSchema build() {
        return new ImmutableObjectSchema(wrappedObjectBuilder.build());
    }
}
