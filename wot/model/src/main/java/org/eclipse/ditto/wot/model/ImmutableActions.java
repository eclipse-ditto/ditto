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
 * Immutable implementation of {@link Actions}.
 */
@Immutable
final class ImmutableActions extends AbstractMap<String, Action> implements Actions {

    private final Map<String, Action> actions;

    ImmutableActions(final Map<String, Action> actions) {
        this.actions = checkNotNull(actions, "actions");
    }

    @Override
    public Optional<Action> getAction(final CharSequence actionName) {
        return Optional.ofNullable(actions.get(actionName.toString()));
    }

    @Override
    public Set<Entry<String, Action>> entrySet() {
        return actions.entrySet();
    }

    @Override
    public JsonObject toJson() {
        return actions.entrySet().stream()
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
        final ImmutableActions that = (ImmutableActions) o;
        return Objects.equals(actions, that.actions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "actions=" + actions +
                "]";
    }
}
