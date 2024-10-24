/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

/**
 * Immutable model representing a permission check for a resource and entity.
 * <p>
 * This class encapsulates a check to verify permissions for a specific resource (e.g., Thing or Policy)
 * associated with an entityId. It holds information about the resource, entity, and the permissions that
 * are being checked.
 * <p>
 * The class is immutable and provides methods to convert from/to JSON, ensuring safe use across threads.
 *
 * @since 3.7.0
 */
@Immutable
public final class ImmutablePermissionCheck {

    private final String resource;
    private final String entityId;
    private final Boolean isPolicyResource;
    private final List<String> hasPermissions;

    /**
     * Constant defining a policy resource path.
     */
    public static final String POLICY_RESOURCE = "policy:/";

    private ImmutablePermissionCheck(final String resource, final String entityId, Boolean isPolicyResource,
            final List<String> hasPermissions) {
        this.resource = resource;
        this.entityId = entityId;
        this.isPolicyResource = resource.contains(POLICY_RESOURCE);
        this.hasPermissions = List.copyOf(hasPermissions);
    }

    /**
     * Creates an {@code ImmutablePermissionCheck} instance.
     *
     * @param resource the resource for which permissions are being checked.
     * @param entityId the entity ID associated with the resource.
     * @param hasPermissions the list of permissions being checked.
     * @return a new {@link ImmutablePermissionCheck} instance.
     */
    public static ImmutablePermissionCheck of(final String resource, final String entityId,
            final List<String> hasPermissions) {

        return new ImmutablePermissionCheck(resource, entityId, resource.contains(POLICY_RESOURCE), hasPermissions);
    }

    /**
     * Returns the resource for which permissions are being checked.
     *
     * @return the resource string.
     */
    public String getResource() {
        return resource;
    }

    /**
     * Returns whether the resource is a policy resource.
     *
     * @return {@code true} if the resource is a policy, {@code false} otherwise.
     */
    public Boolean isPolicyResource() {
        return isPolicyResource;
    }

    /**
     * Returns the entity ID associated with the resource.
     *
     * @return the entity ID string.
     */
    public String getEntityId() {
        return entityId;
    }

    /**
     * Returns the list of permissions being checked.
     *
     * @return the list of permissions.
     */
    public List<String> getHasPermissions() {
        return hasPermissions;
    }

    /**
     * Converts the {@code ImmutablePermissionCheck} object to a {@link JsonObject}.
     *
     * @return the JSON representation of the permission check.
     */
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set("resource", resource)
                .set("entityId", entityId)
                .set("isPolicyResource", isPolicyResource)
                .set("hasPermissions", hasPermissions.toString())
                .build();
    }

    /**
     * Creates an {@code ImmutablePermissionCheck} from a {@link JsonObject}.
     *
     * @param jsonObject the JSON object to parse.
     * @return the parsed {@link ImmutablePermissionCheck}.
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
