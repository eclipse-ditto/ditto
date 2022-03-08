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
 * Immutable implementation of {@link Titles}.
 */
@Immutable
final class ImmutableTitles extends AbstractMap<Locale, Title> implements Titles {

    private final Map<Locale, Title> titles;

    ImmutableTitles(final Map<Locale, Title> titles) {
        this.titles = Collections.unmodifiableMap(new LinkedHashMap<>(checkNotNull(titles, "titles")));
    }

    @Override
    public Set<Entry<Locale, Title>> entrySet() {
        return titles.entrySet();
    }

    @Override
    public JsonObject toJson() {
        return titles.entrySet().stream()
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
        final ImmutableTitles that = (ImmutableTitles) o;
        return Objects.equals(titles, that.titles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(titles);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "titles=" + titles +
                "]";
    }
}
