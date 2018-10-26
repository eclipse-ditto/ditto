/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link PolicyImport}.
 */
@Immutable
final class ImmutablePolicyImport implements PolicyImport {

    private final String importedPolicyId;
    private final EffectedImportedEntries effectedImportedEntries;

    private ImmutablePolicyImport(final String importedPolicyId, final EffectedImportedEntries effectedPermissions) {
        this.importedPolicyId = checkNotNull(importedPolicyId, "imported policy id");
        this.effectedImportedEntries = checkNotNull(effectedPermissions, "effected imported entries");
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} and {@code jsonValue}.
     *
     * @param importedPolicyId the JSON key which is assumed to be ...
     * @param jsonValue the JSON value containing the effected permissions for the PolicyImport. This value is supposed to
     * be a {@link JsonObject}.
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws DittoJsonException if {@code jsonValue} is not a JSON object or the JSON has not the expected format.
     */
    public static PolicyImport of(final String importedPolicyId, final JsonValue jsonValue) {
        checkNotNull(jsonValue, "JSON value");

        final EffectedImportedEntries effectedImportedEntries = Optional.of(jsonValue)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(object -> wrapJsonRuntimeException(() -> ImmutableEffectedImportedEntries.fromJson(object)))
                .orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                        .message("The JSON object for the 'entries' (included/excluded) of the 'imported policy' with ID '" +
                                importedPolicyId + "' is missing or not an object.")
                        .build()));

        return of(importedPolicyId, effectedImportedEntries);
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} with empty
     * {@code effectedImportedEntries}.
     *
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyImport of(final String importedPolicyId) {
        return new ImmutablePolicyImport(importedPolicyId, PoliciesModelFactory.emptyEffectedImportedEntries());
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} and {@code effectedImportedEntries}.
     *
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyImport of(final String importedPolicyId, final EffectedImportedEntries effectedImportedEntries) {
        return new ImmutablePolicyImport(importedPolicyId, effectedImportedEntries);
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
    public static PolicyImport fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        return jsonObject.stream()
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                .findFirst()
                .map(field -> of(field.getKeyName(), field.getValue()))
                .orElseThrow(() -> new DittoJsonException(JsonMissingFieldException.newBuilder()
                        .message("The JSON object for the 'imported policy' is missing.")
                        .build()));
    }

    @Override
    public String getImportedPolicyId() {
        return importedPolicyId;
    }


    @Override
    public EffectedImportedEntries getEffectedImportedEntries() {
        return effectedImportedEntries;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        return effectedImportedEntries.toJson(schemaVersion, thePredicate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePolicyImport resource = (ImmutablePolicyImport) o;
        return Objects.equals(importedPolicyId, resource.importedPolicyId) &&
                Objects.equals(effectedImportedEntries, resource.effectedImportedEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importedPolicyId, effectedImportedEntries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "importedPolicyId=" + importedPolicyId +
                ", effectedImportedEntries=" + effectedImportedEntries +
                "]";
    }

}
