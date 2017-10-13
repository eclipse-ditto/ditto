/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Represents a {@link Resource}'s Key.
 */
public interface ResourceKey extends CharSequence {

    /**
     * The delimiter of the resource JSON key.
     */
    String KEY_DELIMITER = ":";

    /**
     * Returns a ResourceKey for the given character sequence. The {@code typeWithPath} must contain a
     * "{@value KEY_DELIMITER}" to separate Resource type and Resource path.
     * If the given key value is already a ResourceKey, this is immediately properly cast and returned.
     *
     * @param typeWithPath the character sequence value of the ResourceKey to be created.
     * @return a new Label with {@code typeWithPath} as its value.
     * @throws NullPointerException if {@code typeWithPath} is {@code null}.
     * @throws IllegalArgumentException if {@code typeWithPath} is empty.
     */
    static ResourceKey newInstance(final CharSequence typeWithPath) {
        return PoliciesModelFactory.newResourceKey(typeWithPath);
    }

    /**
     * Returns a {@code ResourceKey} for the given {@code type} and {@code path}.
     *
     * @param type the type value of the ResourceKey to be created.
     * @param path the path value of the ResourceKey to be created.
     * @return a new ResourceKey.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty.
     */
    static ResourceKey newInstance(final CharSequence type, final CharSequence path) {
        return PoliciesModelFactory.newResourceKey(type, path);
    }

    /**
     * Returns the type of the ResourceKey.
     *
     * @return the type of the ResourceKey.
     */
    String getResourceType();

    /**
     * Returns the path of the ResourceKey as {@link JsonPointer}.
     *
     * @return the path of the ResourceKey as {@link JsonPointer}.
     */
    JsonPointer getResourcePath();

    /**
     * Returns the JsonFieldDefinition for this Label.
     *
     * @return the JsonFieldDefinition for this Label.
     */
    default JsonFieldDefinition getJsonFieldDefinition() {
        return JsonFactory.newStringFieldDefinition(this, FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

}
