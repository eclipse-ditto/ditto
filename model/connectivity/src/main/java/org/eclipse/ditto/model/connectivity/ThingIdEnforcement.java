/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.model.connectivity;

import java.util.Objects;
import java.util.Set;

/**
 * Holds the required data to apply enforcement of Thing IDs. The target can be an arbitrary String containing the ID,
 * which must match against the passed filters (which may contain placeholders like {@code {{ thing:id }}} etc.
 */
public class ThingIdEnforcement {

    private final String target;
    private final Set<String> filters;

    private ThingIdEnforcement(final String target, final Set<String> filters) {
        this.target = target;
        this.filters = filters;
    }

    public static ThingIdEnforcement of(final String target, final Set<String> filters) {
        return new ThingIdEnforcement(target, filters);
    }

    public String getTarget() {
        return target;
    }

    public Set<String> getFilters() {
        return filters;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ThingIdEnforcement that = (ThingIdEnforcement) o;
        return Objects.equals(target, that.target) &&
                Objects.equals(filters, that.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, filters);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "target=" + target +
                ", filters=" + filters +
                "]";
    }
}