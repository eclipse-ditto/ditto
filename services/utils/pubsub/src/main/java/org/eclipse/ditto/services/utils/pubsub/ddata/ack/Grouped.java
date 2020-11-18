/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata.ack;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Model of a set of values with an optional group.
 *
 * @param <T> type of the (optionally grouped) values.
 */
public final class Grouped<T> {

    @Nullable private final String group;
    private final Set<T> values;

    private Grouped(@Nullable final String group, final Set<T> values) {
        this.group = group;
        this.values = checkNotNull(values, "values");
    }

    /**
     * Create a grouped set of values without group name.
     *
     * @param values the values.
     * @param <T> the type of values.
     * @return the grouped values.
     */
    public static <T> Grouped<T> of(final Set<T> values) {
        return new Grouped<>(null, values);
    }

    /**
     * Create a grouped set of values.
     *
     * @param group the group name, or null for values without a group name.
     * @param values the values.
     * @param <T> the type of values.
     * @return the grouped values.
     */
    public static <T> Grouped<T> of(@Nullable final String group, final Set<T> values) {
        return new Grouped<>(group, values);
    }

    /**
     * Retrieve the optional group name.
     *
     * @return the optional group name.
     */
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    /**
     * Retrieve the set of values.
     *
     * @return the set of values.
     */
    public Set<T> getValues() {
        return values;
    }
}
