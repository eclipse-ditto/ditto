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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link ResourcePermissions}.
 * This class encapsulates a resource's key and its associated permissions.
 * @since 3.7.0
 */
@Immutable
final class ImmutableResourcePermissions implements ResourcePermissions {

    private final ResourceKey resourceKey;
    private final List<String> permissions;

    private static final JsonFieldDefinition<String> RESOURCE_KEY_FIELD =
            JsonFactory.newStringFieldDefinition("resourceKey");
    private static final JsonFieldDefinition<JsonArray> PERMISSIONS_FIELD =
            JsonFactory.newJsonArrayFieldDefinition("hasPermissions");


    private ImmutableResourcePermissions(final ResourceKey resourceKey, final List<String> permissions) {
        this.resourceKey = checkNotNull(resourceKey, "resourceKey");
        this.permissions = Collections.unmodifiableList(permissions);
    }

    /**
     * Returns a new instance of {@code ImmutableResourcePermissions} based on the provided {@code resourceKey}
     * and {@code permissions}.
     *
     * @param resourceKey the key of the resource, containing resourceType and resourcePath.
     * @param permissions the permissions associated with this resource.
     * @return a new {@code ImmutableResourcePermissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableResourcePermissions newInstance(final ResourceKey resourceKey, final List<String> permissions) {
        return new ImmutableResourcePermissions(
                checkNotNull(resourceKey, "resourceKey"),
                checkNotNull(permissions, "permissions")
        );
    }

    /**
     * Creates an {@code ImmutableResourcePermissions} instance from the given JSON object using {@code resourceKey}.
     * The resourceKey is constructed from resourceType and resourcePath inside the JSON.
     *
     * @param jsonObject the JSON object containing the resource permission data.
     * @return an {@code ImmutableResourcePermissions} instance.
     * @throws DittoJsonException if the JSON object is not valid or missing required fields.
     */
    public static ImmutableResourcePermissions fromJson(final JsonObject jsonObject) {
        final String resourceKeyField = jsonObject.getValueOrThrow(RESOURCE_KEY_FIELD);
        final ResourceKey resourceKey = ResourceKey.newInstance(resourceKeyField);
        final List<String> permissions = jsonObject.getValueOrThrow(PERMISSIONS_FIELD)
                .stream().map(JsonValue::asString)
                .collect(Collectors.toList());

        return newInstance(resourceKey, permissions);
    }

    @Override
    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    @Override
    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public JsonObject toJson() {
        JsonObjectBuilder jsonBuilder = JsonFactory.newObjectBuilder();
        jsonBuilder.set(RESOURCE_KEY_FIELD, resourceKey.toString());
        jsonBuilder.set(PERMISSIONS_FIELD, JsonArray.of(permissions));
        return jsonBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableResourcePermissions that = (ImmutableResourcePermissions) o;
        return Objects.equals(resourceKey, that.resourceKey) &&
                Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceKey, permissions);
    }

    @Override
    @Nonnull
    public String toString() {
        return "ResourcePermissions [resourceKey=" + resourceKey + ", permissions=" + permissions + "]";
    }
}
