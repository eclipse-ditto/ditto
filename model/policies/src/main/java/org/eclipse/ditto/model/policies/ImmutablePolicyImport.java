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
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link PolicyImport}.
 */
@Immutable
final class ImmutablePolicyImport implements PolicyImport {

    private final PolicyId importedPolicyId;
    private final boolean isProtected;
    private final EffectedImports effectedImports;

    private ImmutablePolicyImport(final PolicyId importedPolicyId, final boolean isProtected,
            final EffectedImports effectedPermissions) {
        this.importedPolicyId = checkNotNull(importedPolicyId, "imported policy id");
        this.isProtected = isProtected;
        this.effectedImports = checkNotNull(effectedPermissions, "effected imported entries");
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} with empty
     * {@code effectedImports}.
     *
     * @param importedPolicyId TODO TJ doc
     * @param isProtected
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyImport of(final PolicyId importedPolicyId, final boolean isProtected) {
        return new ImmutablePolicyImport(importedPolicyId, isProtected,
                PoliciesModelFactory.emptyEffectedImportedEntries());
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} and {@code effectedImports}.
     *
     * @param importedPolicyId TODO TJ doc
     * @param isProtected
     * @param effectedImports
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyImport of(final PolicyId importedPolicyId, final boolean isProtected,
            final EffectedImports effectedImports) {
        return new ImmutablePolicyImport(importedPolicyId, isProtected, effectedImports);
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} and {@code jsonValue}.
     *
     * @param importedPolicyId the JSON key which is assumed to be ... TODO TJ doc
     * @param jsonValue the JSON value containing the effected permissions for the PolicyImport. This value is supposed to
     * be a {@link JsonObject}.
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws DittoJsonException if {@code jsonValue} is not a JSON object or the JSON has not the expected format.
     */
    public static PolicyImport fromJson(final PolicyId importedPolicyId, final JsonValue jsonValue) {
        checkNotNull(jsonValue, "JSON value");

        final Optional<JsonObject> jsonFields = Optional.of(jsonValue)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject);

        final boolean isProtected = jsonFields
                .map(object -> wrapJsonRuntimeException(() -> object.getValueOrThrow(JsonFields.PROTECTED)))
                .orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                        .message("The JSON object containing the 'protected' value of the 'imported policy' with ID '" +
                                importedPolicyId + "' is missing or not an object.")
                        .build()));
        final EffectedImports effectedImports = jsonFields
                .map(object -> wrapJsonRuntimeException(() -> ImmutableEffectedImports.fromJson(object)))
                .orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                        .message("The JSON object for the 'entries' (included/excluded) of the 'imported policy' with ID '" +
                                importedPolicyId + "' is missing or not an object.")
                        .build()));

        return of(importedPolicyId, isProtected, effectedImports);
    }

    @Override
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    @Override
    public boolean isProtected() {
        return isProtected;
    }

    @Override
    public EffectedImports getEffectedImports() {
        return effectedImports;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.PROTECTED, isProtected, predicate);
        jsonObjectBuilder.setAll(effectedImports.toJson(schemaVersion, thePredicate));
        return jsonObjectBuilder.build();
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
                isProtected == resource.isProtected &&
                Objects.equals(effectedImports, resource.effectedImports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importedPolicyId, isProtected, effectedImports);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "importedPolicyId=" + importedPolicyId +
                ", isProtected=" + isProtected +
                ", effectedImports=" + effectedImports +
                "]";
    }

}
