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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link PolicyImport}.
 */
@Immutable
final class ImmutablePolicyImport implements PolicyImport {

    private final PolicyId importedPolicyId;
    @Nullable private final EffectedImports effectedImports;

    private ImmutablePolicyImport(final PolicyId importedPolicyId,
            @Nullable final EffectedImports effectedPermissions) {
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.effectedImports = effectedPermissions;
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} with empty
     * {@code effectedImports}.
     *
     * @param importedPolicyId the {@code PolicyId} where entries will be imported from.
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyImport of(final PolicyId importedPolicyId) {
        return new ImmutablePolicyImport(importedPolicyId, null);
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} and {@code effectedImports}.
     *
     * @param importedPolicyId the {@code PolicyId} where entries will be imported from.
     * @param effectedImports lists every {@code PolicyEntry} label from the imported {@code Policy} that will be included - if {@code null}, all policy entries will be imported.
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if {@code importedPolicyId} is {@code null}.
     */
    public static PolicyImport of(final PolicyId importedPolicyId, @Nullable final EffectedImports effectedImports) {
        return new ImmutablePolicyImport(importedPolicyId, effectedImports);
    }

    /**
     * Creates a new {@code PolicyImport} object based on the given {@code importedPolicyId} and {@code jsonValue}.
     *
     * @param importedPolicyId the {@code PolicyId} where entries will be imported from.
     * @param jsonValue the JSON value containing the effected permissions for the PolicyImport. This value is supposed to
     * be a {@link JsonObject}.
     * @return a new {@code PolicyImport} object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws DittoJsonException if {@code jsonValue} is not a JSON object or the JSON has not the expected format.
     */
    public static PolicyImport fromJson(final PolicyId importedPolicyId, final JsonValue jsonValue) {
        checkNotNull(jsonValue, "jsonValue");

        final Optional<JsonObject> jsonFields = Optional.of(jsonValue)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject);

        final JsonObject jsonObject = wrapJsonRuntimeException(() ->
                jsonFields.orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                        .message("The value of the policy import of the 'imported policy' with ID '" +
                                importedPolicyId + "' is missing or not an object.")
                        .build())));

        final EffectedImports effectedImports = jsonObject.isEmpty() ? null :
                ImmutableEffectedImports.fromJson(jsonObject);
        return of(importedPolicyId, effectedImports);
    }

    @Override
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    @Override
    public Optional<EffectedImports> getEffectedImports() {
        return Optional.ofNullable(effectedImports);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        if (null != effectedImports) {
            jsonObjectBuilder.setAll(effectedImports.toJson(schemaVersion, thePredicate));
        }
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
                Objects.equals(effectedImports, resource.effectedImports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importedPolicyId, effectedImports);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "importedPolicyId=" + importedPolicyId +
                ", effectedImports=" + effectedImports +
                "]";
    }

}
