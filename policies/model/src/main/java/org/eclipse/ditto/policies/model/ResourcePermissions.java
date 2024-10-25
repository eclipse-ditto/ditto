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

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

import java.util.List;

/**
 * Represents the permissions associated with a resource.
 * @since 3.7.0
 */
public interface ResourcePermissions extends Jsonifiable<JsonObject> {

    /**
     * Gets the key of the resource, which contains both type and path information.
     *
     * @return the resource key.
     */
    ResourceKey getResourceKey();

    /**
     * Gets the permissions associated with the resource.
     *
     * @return the set of permissions.
     */
    List<String> getPermissions();
}
