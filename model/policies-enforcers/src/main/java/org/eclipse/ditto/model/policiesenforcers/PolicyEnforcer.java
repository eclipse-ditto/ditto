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
package org.eclipse.ditto.model.policiesenforcers;

import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.ResourceKey;

/**
 * Abstraction for algorithms operating on a Policy and finding out whether specified {@link Permissions} are existing
 * on a {@code resource} for a given {@link AuthorizationContext}.
 */
public interface PolicyEnforcer {

    /**
     * Checks whether for the Policy of this evaluator the {@code authorizationContext} either implicitly or explicitly
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
     * Checks whether for the Policy of this evaluator the {@code authorizationContext} either implicitly or explicitly
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
     * Returns a set of subject ids each of which has all the given permissions granted on exactly the given resource,
     * and a set of subject ids each of which has 1 or more given permissions revoked on the given resource. Does not
     * consider "REVOKE"s down in the hierarchy.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for.
     * @param permission the permission to check.
     * @param furtherPermissions further permissions to check.
     * @return An {@code EffectedSubjectIds} object containing the grant set and the revoke set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default EffectedSubjectIds getSubjectIdsWithPermission(final ResourceKey resourceKey,
            final String permission, final String... furtherPermissions) {

        return getSubjectIdsWithPermission(resourceKey, Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Returns a set of subject ids each of which has all the given permissions granted on exactly the given resource,
     * and a set of subject ids each of which has 1 or more given permissions revoked on the given resource. Does not
     * consider "REVOKE"s down in the hierarchy.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to check the permission(s) for.
     * @param permissions the permissions to check
     * @return An {@code EffectedSubjectIds} object containing the grant set and the revoke set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    EffectedSubjectIds getSubjectIdsWithPermission(ResourceKey resourceKey, Permissions permissions);

    /**
     * Returns a set of subject ids each of which has all the given permissions granted on the given resource or on any
     * sub resource down in the hierarchy. Revoked permissions are not taken into account.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to use as starting point to check the
     * partial permission(s) in the hierarchy for.
     * @param permission the permission to check.
     * @param furtherPermissions further permissions to check.
     * @return A Set containing the subject ids with partial permissions on the passed resourceKey or any other
     * resources in the hierarchy below.
     * @throws NullPointerException if any argument is {@code null}.
     *
     */
    default Set<String> getSubjectIdsWithPartialPermission(final ResourceKey resourceKey,
            final String permission, final String... furtherPermissions) {

        return getSubjectIdsWithPartialPermission(resourceKey, Permissions.newInstance(permission, furtherPermissions));
    }

    /**
     * Returns a set of subject ids each of which has all the given permissions granted on the given resource or on any
     * sub resource down in the hierarchy. Revoked permissions are not taken into account.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to use as starting point to check the
     * partial permission(s) in the hierarchy for.
     * @param permissions the permissions to be checked.
     * @return A Set containing the subject ids with partial permissions on the passed resourceKey or any other
     * resources in the hierarchy below.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Set<String> getSubjectIdsWithPartialPermission(ResourceKey resourceKey, Permissions permissions);

    /**
     * Checks whether for the Policy of this evaluator the {@code authorizationContext} either implicitly or explicitly
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
     * Checks whether for the Policy of this evaluator the {@code authorizationContext} either implicitly or explicitly
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
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link JsonObject} or a {@link
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
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link JsonObject} or a {@link
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
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link JsonObject} or a {@link
     * org.eclipse.ditto.json.JsonObjectBuilder}) for {@code authorizationContext} and {@code permissions} with some
     * fields white-listed. The resulting {@code JsonObject} only contains those {@code JsonFields} for which the {@code
     * authorizationContext} has the required permissions or those that are present in the white list. Fields in the
     * white list are not present in the output if the authorization subjects are not granted any rights in this policy
     * at all.
     *
     * @param resourceKey the ResourceKey (containing Resource type and path) to start from.
     * @param jsonFields the full JsonFields from which to build the view based on the permissions.
     * @param authorizationContext the AuthorizationContext containing the AuthorizationSubjects.
     * @param permissions the permissions.
     * @param whiteList white-listed fields to be present in the output as long as the authorization subjects are
     * relevant, i. e., some of them are granted the required permissions on some resource.
     * @return a view of the passed {@code jsonFields} as JsonObject for which the required permissions are given.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default JsonObject buildJsonView(final ResourceKey resourceKey,
            final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext,
            final JsonFieldSelector whiteList,
            final Permissions permissions) {

        final JsonObject enforcedJsonView =
                buildJsonView(resourceKey, jsonFields, authorizationContext, permissions);

        final ResourceKey rootResourceKey = PoliciesModelFactory.newResourceKey(resourceKey.getResourceType(),
                JsonFactory.emptyPointer());
        final boolean isAuthorizationSubjectRelevant =
                hasPartialPermissions(rootResourceKey, authorizationContext, permissions);
        if (isAuthorizationSubjectRelevant) {
            final JsonObject inputJsonObject = JsonFactory.newObjectBuilder()
                    .setAll(jsonFields)
                    .build();
            final JsonObject whitelistedJsonView = inputJsonObject.get(whiteList);
            return new JsonObjectMerger().apply(whitelistedJsonView, enforcedJsonView);
        } else {
            return enforcedJsonView;
        }
    }

    /**
     * Builds a view of the passed {@code jsonFields} (e.g. a {@link JsonObject} or a {@link
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
