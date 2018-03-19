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
package org.eclipse.ditto.model.enforcers;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.ThingsModelFactory;

/**
 * Abstraction for algorithms operating on an access control list and finding out whether specified permissions
 * are existing on a resource for a given authorization context. There is no special treatment for the permission
 * {@code ADMINISTRATE}. To check for {@code ADMINISTRATE}, the caller must pass it as an explicit policy permission.
 */
@Immutable
public final class AclEnforcer implements Enforcer {

    private static final Set<String> THING_PERMISSION_NAMES =
            Arrays.stream(org.eclipse.ditto.model.things.Permission.values())
                    .map(Enum::name)
                    .collect(Collectors.toSet());

    private final AccessControlList acl;

    private AclEnforcer(final AccessControlList acl) {
        this.acl = acl;
    }

    /**
     * Construct an ACL enforcer.
     *
     * @param acl the access control list.
     * @return the ACL enforcer.
     */
    public static AclEnforcer of(final AccessControlList acl) {
        return new AclEnforcer(acl);
    }

    @Override
    public boolean hasUnrestrictedPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {
        final org.eclipse.ditto.model.things.Permissions mappedPermissions = mapPermissions(permissions);
        if (mappedPermissions.isEmpty()) {
            return false;
        } else {
            final Set<AuthorizationSubject> grantedSubjects = acl.getAuthorizedSubjectsFor(mappedPermissions);
            return authorizationContext.getAuthorizationSubjects()
                    .stream()
                    .anyMatch(grantedSubjects::contains);
        }
    }

    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey, final AuthorizationContext authorizationContext,
            final Permissions permissions) {
        return hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions);
    }

    @Override
    public JsonObject buildJsonView(final ResourceKey resourceKey, final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext, final Permissions permissions) {
        return hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions)
                ? JsonFactory.newObjectBuilder(jsonFields).build()
                : JsonFactory.newObject();
    }

    @Override
    public EffectedSubjectIds getSubjectIdsWithPermission(final ResourceKey resourceKey,
            final Permissions permissions) {
        final Set<String> grantedSubjects = getSubjectIdsWithPartialPermission(resourceKey, permissions);
        final Set<String> revokedSubjects = getComplementSet(grantedSubjects);
        return ImmutableEffectedSubjectIds.of(grantedSubjects, revokedSubjects);
    }

    @Override
    public Set<String> getSubjectIdsWithPartialPermission(final ResourceKey resourceKey,
            final Permissions permissions) {
        return acl.getAuthorizedSubjectsFor(mapPermissions(permissions))
                .stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Compute the complement set of a set of subject IDs with respect to subject IDs mentioned in the ACL.
     *
     * @param excludedSet a set of subject IDs.
     * @return complement of {@code excludedSet}.
     */
    private Set<String> getComplementSet(final Set<String> excludedSet) {
        return acl.stream()
                .flatMap(aclEntry -> {
                    final String subjectId = aclEntry.getAuthorizationSubject().getId();
                    if (excludedSet.contains(subjectId)) {
                        return Stream.empty();
                    } else {
                        return Stream.of(subjectId);
                    }
                })
                .collect(Collectors.toSet());
    }

    /**
     * Map policy permissions to ACL permissions of the same name.
     *
     * @param policyPermissions policy permissions.
     * @return ACL permissions of the same name.
     */
    private static org.eclipse.ditto.model.things.Permissions mapPermissions(final Permissions policyPermissions) {
        final List<org.eclipse.ditto.model.things.Permission> permissionList = policyPermissions.stream()
                .flatMap(name -> {
                    if (THING_PERMISSION_NAMES.contains(name)) {
                        return Stream.of(org.eclipse.ditto.model.things.Permission.valueOf(name));
                    } else {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
        return ThingsModelFactory.newPermissions(permissionList);
    }
}
