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

import org.eclipse.ditto.json.JsonObject;

import java.util.List;

/**
 * A factory class for creating instances of {@link ResourcePermissions}.
 * <p>
 * This class provides utility methods to create {@code ResourcePermissions} instances either from a JSON representation
 * or by providing specific attributes like a {@link ResourceKey} and a list of permissions. The factory helps
 * in constructing the immutable {@link ImmutableResourcePermissions} objects used for managing resource access control
 * in the policy domain.
 *
 * @since 3.7.0
 */
public final class ResourcePermissionFactory {

    /**
     * Creates a {@link ResourcePermissions} instance from the provided {@link JsonObject}.
     * <p>
     * The JSON object should contain fields that define the {@code resourceKey} (which itself includes
     * {@code resourceType} and {@code resourcePath}) and a list of permissions.
     *
     * @param jsonObject the {@link JsonObject} representing the resource permissions.
     * @return an immutable {@link ResourcePermissions} instance parsed from the provided JSON.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if the JSON object does not contain the expected fields.
     */
    public static ResourcePermissions fromJson(final JsonObject jsonObject) {
        return ImmutableResourcePermissions.fromJson(jsonObject);
    }

    /**
     * Creates a new instance of {@link ResourcePermissions} using the provided {@link ResourceKey} and list of permissions.
     * <p>
     * This method is useful when constructing resource permissions programmatically, where the {@code resourceKey}
     * (containing resource type and path) and the associated permissions are already known.
     *
     * @param resourceKey the key of the resource, including resource type and path.
     * @param permissions the list of permissions associated with this resource.
     * @return a new immutable {@link ResourcePermissions} instance.
     * @throws NullPointerException if any of the arguments are null.
     */
    public static ResourcePermissions newInstance(final ResourceKey resourceKey, final List<String> permissions) {
        return ImmutableResourcePermissions.newInstance(resourceKey, permissions);
    }
}
