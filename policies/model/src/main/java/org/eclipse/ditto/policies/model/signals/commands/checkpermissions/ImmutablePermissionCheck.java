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
package org.eclipse.ditto.policies.model.signals.commands.checkpermissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable model representing a permission check for a resource and entity.
 * <p>
 * This class encapsulates a check to verify permissions for a specific resource (e.g., Thing or Policy)
 * associated with an entityId. It holds information about the resource, entity, and the permissions that
 * are being checked.
 * <p>
 * The resource is stored as a plain string in {@code "resourceType:resourcePath"} format
 * (e.g., {@code "thing:/features"}, {@code "policy:/"}). Callers in higher-level modules that require
 * a typed {@code ResourceKey} can convert it via {@code ResourceKey.newInstance(getResource())}.
 * <p>
 * The class is immutable and provides methods to convert from/to JSON, ensuring safe use across threads.
 *
 * @since 3.7.0
 */
@Immutable
public final class ImmutablePermissionCheck implements Jsonifiable<JsonObject> {

    private static final String POLICY_RESOURCE_PREFIX = "policy:";

    private final String resource;
    private final String entityId;
    private final boolean isPolicyResource;
    private final List<String> hasPermissions;

    private static final JsonFieldDefinition<String> RESOURCE_KEY_FIELD =
            JsonFactory.newStringFieldDefinition("resource");
    private static final JsonFieldDefinition<String> ENTITY_ID_FIELD = JsonFactory.newStringFieldDefinition("entityId");
    private static final JsonFieldDefinition<JsonArray> PERMISSIONS_FIELD =
            JsonFactory.newJsonArrayFieldDefinition("hasPermissions");

    private ImmutablePermissionCheck(final String resource, final String entityId,
            final List<String> hasPermissions) {

        this.resource = resource;
        this.entityId = entityId;
        this.isPolicyResource = resource.startsWith(POLICY_RESOURCE_PREFIX);
        this.hasPermissions = Collections.unmodifiableList(new ArrayList<>(hasPermissions));
    }

    /**
     * Creates an {@code ImmutablePermissionCheck} instance.
     *
     * @param resource the resource string in {@code "resourceType:resourcePath"} format
     *                 (e.g., {@code "thing:/features"}, {@code "policy:/"}).
     * @param entityId the entity ID associated with the resource.
     * @param hasPermissions the list of permissions being checked.
     * @return a new {@link ImmutablePermissionCheck} instance.
     */
    public static ImmutablePermissionCheck of(final String resource, final String entityId,
            final List<String> hasPermissions) {

        return new ImmutablePermissionCheck(resource, entityId, hasPermissions);
    }

    /**
     * Returns the resource string for which permissions are being checked,
     * in {@code "resourceType:resourcePath"} format (e.g., {@code "thing:/features"}).
     * <p>
     * Callers that need a typed {@code ResourceKey} can convert via
     * {@code ResourceKey.newInstance(getResource())}.
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
    public boolean isPolicyResource() {
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

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set("resource", resource)
                .set("entityId", entityId)
                .set("isPolicyResource", isPolicyResource)
                .set("hasPermissions", JsonFactory.newArrayBuilder()
                        .addAll(hasPermissions.stream().map(JsonFactory::newValue).collect(Collectors.toList()))
                        .build())
                .build();
    }

    /**
     * Creates an {@code ImmutablePermissionCheck} from a {@link JsonObject}.
     *
     * @param jsonObject the JSON object to parse.
     * @return the parsed {@link ImmutablePermissionCheck}.
     */
    public static ImmutablePermissionCheck fromJson(final JsonObject jsonObject) {
        final String resource = jsonObject.getValueOrThrow(RESOURCE_KEY_FIELD);
        final String entityId = jsonObject.getValueOrThrow(ENTITY_ID_FIELD);
        final JsonArray permissionsArray = jsonObject.getValueOrThrow(PERMISSIONS_FIELD);
        final List<String> permissions = permissionsArray.stream()
                .map(JsonValue::asString)
                .collect(Collectors.toList());

        return ImmutablePermissionCheck.of(resource, entityId, permissions);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutablePermissionCheck that = (ImmutablePermissionCheck) o;
        return isPolicyResource == that.isPolicyResource &&
                Objects.equals(resource, that.resource) &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(hasPermissions, that.hasPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, entityId, isPolicyResource, hasPermissions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "resource=" + resource +
                ", entityId='" + entityId + '\'' +
                ", isPolicyResource=" + isPolicyResource +
                ", hasPermissions=" + hasPermissions +
                ']';
    }
}
