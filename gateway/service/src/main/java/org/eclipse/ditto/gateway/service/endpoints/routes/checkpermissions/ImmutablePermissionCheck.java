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
package org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.JsonFactory;

import javax.annotation.concurrent.Immutable;

import java.util.List;

@Immutable
public final class ImmutablePermissionCheck {

    private final String resource;
    private final String entityId;
    private final List<String> hasPermissions;

    private ImmutablePermissionCheck(final String resource, final String entityId, final List<String> hasPermissions) {
        this.resource = resource;
        this.entityId = entityId;
        this.hasPermissions = List.copyOf(hasPermissions);
    }

    public static ImmutablePermissionCheck of(final String resource, final String entityId,
            final List<String> hasPermissions) {
        return new ImmutablePermissionCheck(resource, entityId, hasPermissions);
    }

    public String getResource() {
        return resource;
    }

    public String getEntityId() {
        return entityId;
    }

    public List<String> getHasPermissions() {
        return hasPermissions;
    }

    /**
     * Converts the model to a JsonObject.
     *
     * @return JsonObject representation of the permission check.
     */
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set("resource", resource)
                .set("entityId", entityId)
                .set("hasPermissions", hasPermissions.toString())
                .build();
    }

    /**
     * Creates a PermissionCheck model from a JsonObject.
     *
     * @param jsonObject the JsonObject containing permission check data.
     * @return ImmutablePermissionCheck object.
     */
    public static ImmutablePermissionCheck fromJson(final JsonObject jsonObject) {
        final String resource = jsonObject.getValueOrThrow(JsonFactory.newStringFieldDefinition("resource"));
        final String entityId = jsonObject.getValueOrThrow(JsonFactory.newStringFieldDefinition("entityId"));
        final JsonArray permissionsArray =
                jsonObject.getValueOrThrow(JsonFactory.newJsonArrayFieldDefinition("hasPermissions"));
        final List<String> permissions = permissionsArray.stream()
                .map(JsonValue::asString)
                .toList();

        return ImmutablePermissionCheck.of(resource, entityId, permissions);
    }
}
