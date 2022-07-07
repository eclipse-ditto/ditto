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
package org.eclipse.ditto.policies.model.enforcers;

import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.ResourceKey;

/**
 * Abstraction for algorithms enforcing {@link org.eclipse.ditto.policies.model.Permissions} on a {@code resource} for a given
 * {@link org.eclipse.ditto.base.model.auth.AuthorizationContext}.
 */
public interface Enforcer {

    /**
     * Checks whether for the {@code authorizationContext} either implicitly or explicitly
     * has "GRANT" for the {@code permissions} on the specified {@code resourceKey} considering "REVOKE"s down in the
     * hierarchy, so if there is a REVOKE for the {@code authorizationContext} somewhere down the hierarchy of the
     * {@code resourceKey}, the result will be {@code false}.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for.
     * @param authorizationContext the authorization context to check.
     * @param permission the permission to check.
     * @param furtherPermissions further permissions to check.
     * @return {@code true} if {@code authorizationContext} has the given permission(s), {@code false} otherwise.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default boolean hasUnrestrictedPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext,
            final String permission,
            final String... furtherPermissions) {

        return hasUnrestrictedPermissions(resourceKey, authorizationContext,
                Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Checks whether for the {@code authorizationContext} either implicitly or explicitly
     * has "GRANT" for the {@code permissions} on the specified set of {@code resourceKeys} considering "REVOKE"s
     * down in the hierarchy, so if there is a REVOKE for the {@code authorizationContext} somewhere down the hierarchy
     * for any of the {@code resourceKeys}, the result will be {@code false}.
     *
     * @param resourceKeys the ResourceKeys (containing Resource type and path) to check the permission(s) for.
     * @param authorizationContext the authorization context to check.
     * @param permission the permission to check.
     * @param furtherPermissions further permissions to check.
     * @return {@code true} if {@code authorizationContext} has the given permission(s), {@code false} otherwise.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default boolean hasUnrestrictedPermissions(final Set<ResourceKey> resourceKeys,
            final AuthorizationContext authorizationContext,
            final String permission,
            final String... furtherPermissions) {

        return resourceKeys.stream()
                .allMatch(resourceKey -> hasUnrestrictedPermissions(resourceKey, authorizationContext,
                        Permissions.newInstance(permission, furtherPermissions)));
    }

    /**
     * Checks whether the {@code authorizationContext} either implicitly or explicitly
     * has "GRANT" for the {@code permissions} on the specified {@code resourceKey} considering "REVOKE"s down in the
     * hierarchy, so if there is a REVOKE for the {@code authorizationContext} somewhere down the hierarchy of the
     * {@code resourceKey}, the result will be {@code false}.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for.
     * @param authorizationContext the authorization context to check.
     * @param permissions the permissions to check.
     * @return {@code true} if {@code authorizationContext} has the given permissions, {@code false} otherwise.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean hasUnrestrictedPermissions(ResourceKey resourceKey, AuthorizationContext authorizationContext,
            Permissions permissions);

    /**
     * Returns a set of authorization subjects each of which has all the given permissions granted on exactly the given
     * resource, and a set of authorization subjects each of which has 1 or more given permissions revoked on the given
     * resource.
     * Does not consider "REVOKE"s down in the hierarchy.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for.
     * @param permission the permission to check.
     * @param furtherPermissions further permissions to check.
     * @return the effected subjects.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    default EffectedSubjects getSubjectsWithPermission(final ResourceKey resourceKey, final String permission,
            final String... furtherPermissions) {

        return getSubjectsWithPermission(resourceKey, Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Returns a set of authorization subjects each of which has all the given permissions granted on exactly the given
     * resource, and a set of authorization subjects each of which has 1 or more given permissions revoked on the given
     * resource.
     * Does not consider "REVOKE"s down in the hierarchy.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for.
     * @param permissions the permissions to check
     * @return the effected subjects.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    EffectedSubjects getSubjectsWithPermission(ResourceKey resourceKey, Permissions permissions);

    /**
     * Returns a set of authorization subjects each of which has all the given permissions granted on the given resource
     * or on any sub resource down in the hierarchy.
     * Revoked permissions are not taken into account.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to use as starting point to check the
     * partial permission(s) in the hierarchy for.
     * @param permissions the permissions to be checked.
     * @return the authorization subjects with partial permissions on the passed resourceKey or any other resources in
     * the hierarchy below.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    Set<AuthorizationSubject> getSubjectsWithPartialPermission(ResourceKey resourceKey, Permissions permissions);

    /**
     * Checks whether the {@code authorizationContext} either implicitly or explicitly
     * has "GRANT" for the specified permissions on the passed in {@code resourceKey} or on any {@code resource} down in
     * the hierarchy of the {@code resourceKey}.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for.
     * @param authorizationContext the authorization context to check.
     * @param permission the permission to check.
     * @param furtherPermissions further permissions to check.
     * @return {@code true} if {@code authorizationContext} has the given permission(s) somewhere down in the hierarchy,
     * {@code false} otherwise.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default boolean hasPartialPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext,
            final String permission,
            final String... furtherPermissions) {

        return hasPartialPermissions(resourceKey, authorizationContext,
                Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Checks whether the {@code authorizationContext} either implicitly or explicitly
     * has "GRANT" for the specified permissions on the passed in {@code resourceKey} or on any {@code resource} down in
     * the hierarchy of the {@code resourceKey}.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for and use for
     * looking downwards the hierarchy.
     * @param authorizationContext the authorization context to check.
     * @param permissions the permission to check.
     * @return {@code true} if {@code authorizationContext} has the given permissions somewhere down in the hierarchy,
     * {@code false} otherwise.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean hasPartialPermissions(ResourceKey resourceKey, AuthorizationContext authorizationContext,
            Permissions permissions);

    /**
     * Returns a set of authorization subjects each of which has all the given permissions granted on the given resource
     * or on any sub resource down in the hierarchy.
     * Revoked permissions are taken into account.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to use as starting point to check the
     * partial permission(s) in the hierarchy for.
     * @param permission the permission to check.
     * @param furtherPermissions further permissions to check.
     * @return the authorization subjects with unrestricted permissions on the passed resourceKey or any other
     * resources in the hierarchy below.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 2.2.0
     */
    default Set<AuthorizationSubject> getSubjectsWithUnrestrictedPermission(ResourceKey resourceKey,
            final String permission, final String... furtherPermissions) {
        return getSubjectsWithUnrestrictedPermission(resourceKey,
                Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Returns a set of authorization subjects each of which has all the given permissions granted on the given resource
     * or on any sub resource down in the hierarchy.
     * Revoked permissions are taken into account.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to use as starting point to check the
     * partial permission(s) in the hierarchy for.
     * @param permissions the permissions to be checked.
     * @return the authorization subjects with unrestricted permissions on the passed resourceKey or any other
     * resources in the hierarchy below.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 2.2.0
     */
    Set<AuthorizationSubject> getSubjectsWithUnrestrictedPermission(ResourceKey resourceKey, Permissions permissions);

    /**
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link org.eclipse.ditto.json.JsonObject} or a {@link
     * org.eclipse.ditto.json.JsonObjectBuilder}) {@code authorizationContext} and {@code permissions}. The resulting
     * {@code JsonObject} only contains {@code JsonFields} for which the {@code authorizationContext} has the required
     * permissions.
     *
     * @param jsonFields the full JsonFields from which to build the view based on the permissions.
     * @param resourceType the type of the Resource
     * @param authorizationContext the AuthorizationContext containing the AuthorizationSubjects.
     * @param permission the permission.
     * @param furtherPermissions further permissions.
     * @return a view of the passed {@code jsonFields} as JsonObject for which the required permissions are given.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code resourceType} is empty.
     */
    default JsonObject buildJsonView(final Iterable<JsonField> jsonFields,
            final CharSequence resourceType,
            final AuthorizationContext authorizationContext,
            final String permission,
            final String... furtherPermissions) {

        return buildJsonView(ResourceKey.newInstance(resourceType, JsonFactory.emptyPointer()), jsonFields,
                authorizationContext, Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link org.eclipse.ditto.json.JsonObject} or a {@link
     * org.eclipse.ditto.json.JsonObjectBuilder}) {@code authorizationContext} and {@code permissions}. The resulting
     * {@code JsonObject} only contains {@code JsonFields} for which the {@code authorizationContext} has the required
     * permissions.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to start from.
     * @param jsonFields the full JsonFields from which to build the view based on the permissions.
     * @param authorizationContext the AuthorizationContext containing the AuthorizationSubjects.
     * @param permission the permission.
     * @param furtherPermissions further permissions.
     * @return a view of the passed {@code jsonFields} as JsonObject for which the required permissions are given.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default JsonObject buildJsonView(final ResourceKey resourceKey,
            final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext,
            final String permission,
            final String... furtherPermissions) {

        return buildJsonView(resourceKey, jsonFields, authorizationContext,
                Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link org.eclipse.ditto.json.JsonObject} or a {@link
     * org.eclipse.ditto.json.JsonObjectBuilder}) for {@code authorizationContext} and {@code permissions} with some
     * fields allowed. The resulting {@code JsonObject} only contains those {@code JsonFields} for which the {@code
     * authorizationContext} has the required permissions or those that are present in the allowlist. Fields in the
     * allow list are not present in the output if the authorization subjects are not granted any rights at all.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to start from.
     * @param jsonFields the full JsonFields from which to build the view based on the permissions.
     * @param authorizationContext the AuthorizationContext containing the AuthorizationSubjects.
     * @param permissions the permissions.
     * @param allowlist allowed fields to be present in the output as long as the authorization subjects are
     * relevant, i.e., some of them are granted the required permissions on some resource.
     * @return a view of the passed {@code jsonFields} as JsonObject for which the required permissions are given.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default JsonObject buildJsonView(final ResourceKey resourceKey,
            final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext,
            final JsonFieldSelector allowlist,
            final Permissions permissions) {

        final JsonObject enforcedJsonView =
                buildJsonView(resourceKey, jsonFields, authorizationContext, permissions);

        final ResourceKey rootResourceKey = ResourceKey.newInstance(resourceKey.getResourceType(),
                JsonFactory.emptyPointer());
        final boolean isAuthorizationSubjectRelevant =
                hasPartialPermissions(rootResourceKey, authorizationContext, permissions);
        if (isAuthorizationSubjectRelevant) {
            final JsonObject inputJsonObject = JsonFactory.newObject(jsonFields);
            final JsonObject allowedJsonView = inputJsonObject.get(allowlist);
            return JsonFactory.newObject(allowedJsonView, enforcedJsonView);
        } else {
            return enforcedJsonView;
        }
    }

    /**
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link org.eclipse.ditto.json.JsonObject} or a {@link
     * org.eclipse.ditto.json.JsonObjectBuilder}) {@code authorizationContext} and {@code permissions}. The resulting
     * {@code JsonObject} only contains {@code JsonFields} for which the {@code authorizationContext} has the required
     * permissions.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to start from for building the view
     * @param jsonFields the full JsonFields from which to build the view based on the permissions.
     * @param authorizationContext the AuthorizationContext containing the AuthorizationSubjects.
     * @param permissions the permissions.
     * @return a view of the passed {@code jsonFields} as JsonObject for which the required permissions are given.
     * @throws NullPointerException if any argument is {@code null}.
     */
    JsonObject buildJsonView(ResourceKey resourceKey, Iterable<JsonField> jsonFields,
            AuthorizationContext authorizationContext, Permissions permissions);

}
