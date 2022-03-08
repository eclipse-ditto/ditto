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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link Descriptions}.
 */
@Immutable
final class ImmutableDescriptions extends AbstractMap<Locale, Description> implements Descriptions {

    private final Map<Locale, Description> descriptions;

    ImmutableDescriptions(final Map<Locale, Description> descriptions) {
        this.descriptions =
                Collections.unmodifiableMap(new LinkedHashMap<>(checkNotNull(descriptions, "descriptions")));
    }

    @Override
    public Set<Entry<Locale, Description>> entrySet() {
        return descriptions.entrySet();
    }

    @Override
    public JsonObject toJson() {
        return descriptions.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey().toString(), JsonValue.of(e.getValue())))
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
        if (!super.equals(o)) {
            return false;
        }
        final ImmutableDescriptions that = (ImmutableDescriptions) o;
        return Objects.equals(descriptions, that.descriptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "descriptions=" + descriptions +
                "]";
    }
}
