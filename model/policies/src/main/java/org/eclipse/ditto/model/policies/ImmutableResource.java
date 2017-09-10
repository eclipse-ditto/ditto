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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link Resource}.
 */
@Immutable
final class ImmutableResource implements Resource {

    private final ResourceKey resourceKey;
    private final EffectedPermissions effectedPermissions;

    private ImmutableResource(final ResourceKey theResourceKey, final EffectedPermissions theEffectedPermissions) {
        resourceKey = checkNotNull(theResourceKey, "Resource key");
        effectedPermissions = checkNotNull(theEffectedPermissions, "effected permissions");
    }

    /**
     * Creates a new {@code Resource} object based on the given {@code resourceKey} and {@code jsonValue}.
     *
     * @param resourceKey the JSON key which is assumed to be the path of a Resource prefixed with a type.
     * @param jsonValue the JSON value containing the effected permissions for the Resource. This value is supposed to
     * be a {@link JsonObject}.
     * @return a new {@code Resource} object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws DittoJsonException if {@code jsonValue} is not a JSON object or the JSON has not the expected format.
     */
    public static Resource of(final ResourceKey resourceKey, final JsonValue jsonValue) {
        checkNotNull(jsonValue, "JSON value");

        final EffectedPermissions effectedPermissions = Optional.of(jsonValue)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(object -> wrapJsonRuntimeException(() -> ImmutableEffectedPermissions.fromJson(object)))
                .orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                        .message("The JSON object is either empty or contains only fields with the schema version.")
                        .build()));

        return of(resourceKey, effectedPermissions);
    }

    /**
     * Creates a new {@code Resource} object based on the given {@code resourceKey} and {@code jsonValue}.
     *
     * @param resourceKey the JSON key which is assumed to be the path of a Resource prefixed with a type.
     * @param effectedPermissions the effected Permissions for this Resource.
     * @return a new {@code Resource} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Resource of(final ResourceKey resourceKey, final EffectedPermissions effectedPermissions) {
        return new ImmutableResource(resourceKey, effectedPermissions);
    }

    /**
     * Creates a new {@code Resource} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Resource to be created.
     * @return a new Resource which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject}
     * <ul>
     *     <li>is empty,</li>
     *     <li>contains only a field with the schema version</li>
     *     <li>or it contains more than two fields.</li>
     * </ul>
     */
    public static Resource fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        return jsonObject.stream()
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                .findFirst()
                .map(field -> of(ResourceKey.newInstance(field.getKeyName()), field.getValue()))
                .orElseThrow(() -> new DittoJsonException(JsonMissingFieldException.newBuilder()
                        .message("The JSON object is either empty or contains only fields with the schema version.")
                        .build()));
    }

    @Override
    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    @Override
    public String getType() {
        return resourceKey.getResourceType();
    }

    @Override
    public JsonPointer getPath() {
        return resourceKey.getResourcePath();
    }

    @Override
    public String getFullQualifiedPath() {
        return resourceKey.toString();
    }

    @Override
    public EffectedPermissions getEffectedPermissions() {
        return effectedPermissions;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        return effectedPermissions.toJson(schemaVersion, thePredicate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableResource resource = (ImmutableResource) o;
        return Objects.equals(resourceKey, resource.resourceKey) &&
                Objects.equals(effectedPermissions, resource.effectedPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceKey, effectedPermissions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "resourceKey=" + resourceKey +
                ", effectedPermissions=" + effectedPermissions +
                "]";
    }

}
