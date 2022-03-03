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

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract implementation of {@link SingleDataSchema.Builder}.
 */
abstract class AbstractSingleDataSchemaBuilder<B extends SingleDataSchema.Builder<B, S>, S extends SingleDataSchema>
        implements SingleDataSchema.Builder<B, S> {

    protected final B myself;
    protected final JsonObjectBuilder wrappedObjectBuilder;

    @SuppressWarnings("unchecked")
    protected AbstractSingleDataSchemaBuilder(final JsonObjectBuilder wrappedObjectBuilder, final Class<?> selfType) {
        myself = (B) selfType.cast(this);
        this.wrappedObjectBuilder = checkNotNull(wrappedObjectBuilder, "wrappedObjectBuilder");
        setType(getDataSchemaType());
    }

    abstract DataSchemaType getDataSchemaType();

    @Override
    public B setAtType(@Nullable final AtType atType) {
        if (atType != null) {
            if (atType instanceof MultipleAtType) {
                putValue(SingleDataSchema.DataSchemaJsonFields.AT_TYPE_MULTIPLE, ((MultipleAtType) atType).toJson());
            } else if (atType instanceof SingleAtType) {
                putValue(SingleDataSchema.DataSchemaJsonFields.AT_TYPE, atType.toString());
            } else {
                throw new IllegalArgumentException("Unsupported @type: " + atType.getClass().getSimpleName());
            }
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.AT_TYPE);
        }
        return myself;
    }

    @Override
    public B setTitle(@Nullable final Title title) {
        if (title != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.TITLE, title.toString());
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.TITLE);
        }
        return myself;
    }

    @Override
    public B setTitles(@Nullable final Titles titles) {
        if (titles != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.TITLES, titles.toJson());
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.TITLES);
        }
        return myself;
    }

    @Override
    public B setDescription(@Nullable final Description description) {
        if (description != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.DESCRIPTION, description.toString());
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.DESCRIPTION);
        }
        return myself;
    }

    @Override
    public B setDescriptions(@Nullable final Descriptions descriptions) {
        if (descriptions != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.DESCRIPTIONS, descriptions.toJson());
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.DESCRIPTIONS);
        }
        return myself;
    }

    @Override
    public B setWriteOnly(@Nullable final Boolean writeOnly) {
        if (writeOnly != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.WRITE_ONLY, writeOnly);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.WRITE_ONLY);
        }
        return myself;
    }

    @Override
    public B setReadOnly(@Nullable final Boolean readOnly) {
        if (readOnly != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.READ_ONLY, readOnly);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.READ_ONLY);
        }
        return myself;
    }

    @Override
    public B setOneOf(@Nullable final Collection<SingleDataSchema> oneOf) {
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
    public B setUnit(@Nullable final String unit) {
        putValue(SingleDataSchema.DataSchemaJsonFields.UNIT, unit);
        return myself;
    }

    @Override
    public B setEnum(@Nullable final Collection<JsonValue> enumValues) {
        if (enumValues != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.ENUM, enumValues.stream()
                    .collect(JsonCollectors.valuesToArray()));
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.ENUM);
        }
        return myself;
    }

    @Override
    public B setFormat(@Nullable final String format) {
        putValue(SingleDataSchema.DataSchemaJsonFields.FORMAT, format);
        return myself;
    }

    @Override
    public B setConst(@Nullable final JsonValue constValue) {
        if (constValue != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.CONST, constValue);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.CONST);
        }
        return myself;
    }

    @Override
    public B setDefault(@Nullable final JsonValue defaultValue) {
        if (defaultValue != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.DEFAULT, defaultValue);
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.DEFAULT);
        }
        return myself;
    }

    @Override
    public B setType(@Nullable final DataSchemaType type) {
        if (type != null) {
            putValue(SingleDataSchema.DataSchemaJsonFields.TYPE, type.toString());
        } else {
            remove(SingleDataSchema.DataSchemaJsonFields.TYPE);
        }
        return myself;
    }

    protected <J> void putValue(final JsonFieldDefinition<J> definition, @Nullable final J value) {
        final Optional<JsonKey> keyOpt = definition.getPointer().getRoot();
        if (keyOpt.isPresent()) {
            final JsonKey key = keyOpt.get();
            if (null != value) {
                checkNotNull(value, definition.getPointer().toString());
                wrappedObjectBuilder.remove(key);
                wrappedObjectBuilder.set(definition, value);
            } else {
                wrappedObjectBuilder.remove(key);
            }
        }
    }

    protected void remove(final JsonFieldDefinition<?> fieldDefinition) {
        wrappedObjectBuilder.remove(fieldDefinition);
    }
}
