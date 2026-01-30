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

    /**
     * Creates a new Actions container from the specified JSON object.
     *
     * @param jsonObject the JSON object containing action definitions.
     * @return the Actions container.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static Actions fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> field.getKey().toString(),
                field -> Action.fromJson(field.getKey().toString(), field.getValue().asObject()),
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Actions: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a new Actions container from the specified collection of actions.
     *
     * @param actions the collection of actions.
     * @return the Actions container.
     * @throws NullPointerException if {@code actions} is {@code null}.
     */
    static Actions from(final Collection<Action> actions) {
        return of(actions.stream().collect(Collectors.toMap(
                Action::getActionName,
                a -> a,
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Actions: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a new Actions container from the specified map of action name to action.
     *
     * @param actions the map of actions.
     * @return the Actions container.
     * @throws NullPointerException if {@code actions} is {@code null}.
     */
    static Actions of(final Map<String, Action> actions) {
        return new ImmutableActions(actions);
    }

    /**
     * Returns the action with the specified name if it exists.
     *
     * @param actionName the name of the action.
     * @return the optional action.
     */
    Optional<Action> getAction(CharSequence actionName);

}
