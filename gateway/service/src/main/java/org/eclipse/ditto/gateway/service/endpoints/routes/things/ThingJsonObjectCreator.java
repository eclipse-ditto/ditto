/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.things;

import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotDeletableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotDeletableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotExplicitlySettableException;

/**
 * Creates a Thing {@link org.eclipse.ditto.json.JsonObject} from a given JSON String for either PUT or PATCH requests.
 */
public class ThingJsonObjectCreator {

    private final String thingIdFromPath;
    private final JsonObject inputJson;
    @Nullable private final JsonValue thingIdFromBody;

    private ThingJsonObjectCreator(final String thingIdFromPath, final JsonObject inputJson) {
        this.thingIdFromPath = thingIdFromPath;
        this.inputJson = inputJson;
        this.thingIdFromBody = inputJson.getValue(Thing.JsonFields.ID.getPointer()).orElse(null);
    }

    static ThingJsonObjectCreator newInstance(final String inputJsonString, final String thingIdFromPath) {
        return new ThingJsonObjectCreator(thingIdFromPath,
                wrapJsonRuntimeException(() -> JsonFactory.newObject(inputJsonString)));
    }

    /**
     * @return a Thing JSON for PUT requests.
     */
    JsonObject forPut() {
        return checkThingIdForPut();
    }

    /**
     * @return a Thing JSON for PATCH requests.
     */
    JsonObject forPatch() {
        checkThingIdForPatch();
        checkPolicyIdForPatch();
        return inputJson;
    }

    private JsonObject checkThingIdForPut() {
        // verifies that thing ID agrees with ID from route
        if (thingIdFromBody != null) {
            if (!thingIdFromBody.isString() || !thingIdFromPath.equals(thingIdFromBody.asString())) {
                throw ThingIdNotExplicitlySettableException.forPutOrPatchMethod().build();
            }
            return inputJson;
        } else {
            return inputJson.toBuilder().set(Thing.JsonFields.ID, thingIdFromPath).build();
        }
    }

    private void checkThingIdForPatch() {
        // verifies that thing ID is not null when PATCHing
        if (thingIdFromBody != null) {
            if (thingIdFromBody.isNull()) {
                throw ThingIdNotDeletableException.newBuilder().build();
            }
            if (!thingIdFromBody.isString() || !thingIdFromPath.equals(thingIdFromBody.asString())) {
                throw ThingIdNotExplicitlySettableException.forPutOrPatchMethod().build();
            }
        }
    }

    private void checkPolicyIdForPatch() {
        // verifies that policy ID is not null when PATCHing
        final Optional<JsonValue> optPolicyId = inputJson.getValue(Thing.JsonFields.POLICY_ID.getPointer());
        if (optPolicyId.isPresent() && optPolicyId.get().isNull()) {
            throw PolicyIdNotDeletableException.newBuilder().build();
        }
    }

}
