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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link MultipleSecurity}.
 */
@Immutable
final class ImmutableMultipleSecurity implements MultipleSecurity {

    private final List<SingleSecurity> securities;

    ImmutableMultipleSecurity(final Collection<SingleSecurity> securities) {
        this.securities = Collections.unmodifiableList(new ArrayList<>(securities));
    }

    @Override
    public Iterator<SingleSecurity> iterator() {
        return securities.iterator();
    }

    @Override
    public JsonArray toJson() {
        return securities.stream()
                .map(SingleSecurity::of)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableMultipleSecurity that = (ImmutableMultipleSecurity) o;
        return Objects.equals(securities, that.securities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(securities);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "securities=" + securities +
                "]";
    }
}
