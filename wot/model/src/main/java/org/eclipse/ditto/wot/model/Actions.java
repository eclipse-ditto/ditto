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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

/**
 * Actions is a container for named {@link Action}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance</a>
 * @since 2.4.0
 */
public interface Actions extends Map<String, Action>, Jsonifiable<JsonObject> {

    static Actions fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> field.getKey().toString(),
                field -> Action.fromJson(field.getKey().toString(), field.getValue().asObject()),
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                LinkedHashMap::new)
        ));
    }

    static Actions from(final Collection<Action> actions) {
        return of(actions.stream().collect(Collectors.toMap(
                Action::getActionName,
                a -> a,
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                LinkedHashMap::new)
        ));
    }

    static Actions of(final Map<String, Action> actions) {
        return new ImmutableActions(actions);
    }

    Optional<Action> getAction(CharSequence actionName);

}
