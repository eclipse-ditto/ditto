/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link EffectedPermissions}.
 */
@Immutable
final class ImmutableEffectedPermissions implements EffectedPermissions {

    private final Permissions grantedPermissions;
    private final Permissions revokedPermissions;

    private ImmutableEffectedPermissions(final Permissions granted, final Permissions revoked) {
        grantedPermissions = granted;
        revokedPermissions = revoked;
    }

    /**
     * Returns a new {@code EffectedPermissions} object of the given {@code grantedPermissions} and
     * {@code revokedPermissions}.
     *
     * @param grantedPermissions the Permissions which should be added as "granted".
     * @param revokedPermissions the Permissions which should be added as "revoked".
     * @return a new {@code EffectedPermissions} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static EffectedPermissions of(final Iterable<String> grantedPermissions,
            final Iterable<String> revokedPermissions) {

        final Permissions granted = toPermissions(toSet(checkNotNull(grantedPermissions, "granted permissions")));
        final Permissions revoked = toPermissions(toSet(checkNotNull(revokedPermissions, "revoked permissions")));

        return new ImmutableEffectedPermissions(granted, revoked);
    }

    private static Collection<String> toSet(final Iterable<String> iterable) {
        final Set<String> result;
        if (iterable instanceof Set) {
            result = (Set<String>) iterable;
        } else {
            result = new HashSet<>();
            iterable.forEach(result::add);
        }
        return result;
    }

    private static Permissions toPermissions(final Collection<String> stringCollection) {
        return stringCollection.isEmpty()
                ? PoliciesModelFactory.noPermissions()
                : PoliciesModelFactory.newPermissions(stringCollection);
    }

    /**
     * Creates a new {@code EffectedPermissions} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the EffectedPermissions to be created.
     * @return a new EffectedPermissions which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if the passed in {@code jsonObject} was not in the expected 'EffectedPermissions'
     * format.
     */
    public static EffectedPermissions fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        final Set<String> granted = wrapJsonRuntimeException(() -> getPermissionsFor(jsonObject, JsonFields.GRANT));
        final Set<String> revoked = wrapJsonRuntimeException(() -> getPermissionsFor(jsonObject, JsonFields.REVOKE));
        return of(granted, revoked);
    }

    private static Set<String> getPermissionsFor(final JsonObject jsonObject,
            final JsonFieldDefinition<JsonArray> effect) {

        return jsonObject.getValueOrThrow(effect)
                .stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Permissions getPermissions(final PermissionEffect effect) {
        switch (checkNotNull(effect, "permission effect")) {
            case GRANT:
                return PoliciesModelFactory.newPermissions(grantedPermissions);
            case REVOKE:
                return PoliciesModelFactory.newPermissions(revokedPermissions);
            default:
                throw new IllegalArgumentException("Permission effect <" + effect + "> is unknown!");
        }
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.GRANT, grantedPermissions.toJson(), predicate)
                .set(JsonFields.REVOKE, revokedPermissions.toJson(), predicate)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableEffectedPermissions that = (ImmutableEffectedPermissions) o;
        return Objects.equals(grantedPermissions, that.grantedPermissions) && Objects
                .equals(revokedPermissions, that.revokedPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grantedPermissions, revokedPermissions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "grantedPermissions=" + grantedPermissions +
                ", revokedPermissions=" + revokedPermissions +
                "]";
    }

}
