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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link Properties}.
 */
@Immutable
final class ImmutableProperties extends AbstractMap<String, Property> implements Properties {

    private final Map<String, Property> properties;

    ImmutableProperties(final Map<String, Property> properties) {
        this.properties = checkNotNull(properties, "properties");
    }

    @Override
    public Optional<Property> getProperty(final CharSequence propertyName) {
        return Optional.ofNullable(properties.get(propertyName.toString()));
    }

    @Override
    public Set<Entry<String, Property>> entrySet() {
        return properties.entrySet();
    }

    @Override
    public JsonObject toJson() {
        return properties.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey(), e.getValue().toJson()))
                .collect(JsonCollectors.fieldsToObject());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableProperties that = (ImmutableProperties) o;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "properties=" + properties +
                "]";
    }
}
