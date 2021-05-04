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
package org.eclipse.ditto.connectivity.api;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * The states an {@code BaseClientActor} can have.
 */
public enum BaseClientState implements Jsonifiable<JsonObject> {
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    INITIALIZED,
    UNKNOWN,
    TESTING;

    /**
     * JSON field of the name.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_NAME =
            JsonFactory.newStringFieldDefinition("name");

    /**
     * Creates a new {@link BaseClientState} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the created instance.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     * @throws IllegalArgumentException if the passed jsonObject contains a name that is no constant of this enum.
     */
    public static BaseClientState fromJson(final JsonObject jsonObject) {
        final String name = checkNotNull(jsonObject, "jsonObject").getValueOrThrow(JSON_KEY_NAME);
        return BaseClientState.valueOf(name);
    }

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set(JSON_KEY_NAME, name())
                .build();
    }
}
