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
package org.eclipse.ditto.services.models.policies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListBuilder;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;

/**
 * Utilities for migrating Policies and ACLs between {@link org.eclipse.ditto.model.base.json.JsonSchemaVersion#V_1} to
 * {@link org.eclipse.ditto.model.base.json.JsonSchemaVersion#V_2}.
 */
public final class PoliciesAclMigrations {

    /**
     * The prefix for migrated labels.
     */
    public static final String ACL_LABEL_PREFIX = "acl_";

    private static final JsonPointer ROOT_PATH = JsonPointer.empty();

    private PoliciesAclMigrations() {
        throw new AssertionError();
    }

    /**
     * Migrates the passed {@code AccessControlList} into a {@code Policy}.
     *
     * @param accessControlList the ACL to migrate.
     * @param policyId the ID for the migrated Policy.
     * @return the Policy.
     */
    public static Policy accessControlListToPolicyEntries(final AccessControlList accessControlList,
            final String policyId) {
        final PolicyBuilder policyBuilder = PoliciesModelFactory.newPolicyBuilder(policyId);
        accessControlList.getEntriesSet().forEach(aclEntry -> {
            final String sid = aclEntry.getAuthorizationSubject().getId();
            final PolicyBuilder.LabelScoped labelScoped = policyBuilder.forLabel(ACL_LABEL_PREFIX + sid);

            labelScoped.setSubject(SubjectIssuer.GOOGLE_URL, sid, SubjectType.JWT);
            labelScoped.setSubject(SubjectIssuer.GOOGLE_URL, sid, SubjectType.JWT);

            if (aclEntry.getPermissions().contains(org.eclipse.ditto.model.things.Permission.READ) &&
                    aclEntry.getPermissions()
                            .contains(org.eclipse.ditto.model.things.Permission.WRITE)) {
                labelScoped.setGrantedPermissions(PoliciesResourceType.policyResource(ROOT_PATH), Permission.READ);
                labelScoped.setGrantedPermissions(PoliciesResourceType.thingResource(ROOT_PATH), Permission.READ,
                        Permission.WRITE);
                labelScoped.setGrantedPermissions(PoliciesResourceType.messageResource(ROOT_PATH), Permission.READ,
                        Permission.WRITE);
            } else if (aclEntry.getPermissions().contains(org.eclipse.ditto.model.things.Permission.READ)) {
                labelScoped.setGrantedPermissions(PoliciesResourceType.policyResource(ROOT_PATH), Permission.READ);
                labelScoped.setGrantedPermissions(PoliciesResourceType.thingResource(ROOT_PATH), Permission.READ);
                labelScoped.setGrantedPermissions(PoliciesResourceType.messageResource(ROOT_PATH), Permission.READ);
            } else if (aclEntry.getPermissions().contains(org.eclipse.ditto.model.things.Permission.WRITE)) {
                labelScoped.setGrantedPermissions(PoliciesResourceType.thingResource(ROOT_PATH), Permission.WRITE);
                labelScoped.setGrantedPermissions(PoliciesResourceType.messageResource(ROOT_PATH), Permission.WRITE);
            }

            if (aclEntry.getPermissions().contains(org.eclipse.ditto.model.things.Permission.ADMINISTRATE)) {
                // allow reading+writing policy:/ if the ACL entry has Administrate permission
                labelScoped.setGrantedPermissions(PoliciesResourceType.policyResource(ROOT_PATH),
                        Permission.READ, Permission.WRITE);
                // allow writing thing:/acl if the ACL entry has Administrate permission
                labelScoped.setGrantedPermissions(PoliciesResourceType.thingResource(Thing.JsonFields.ACL.getPointer()),
                        Permission.READ, Permission.WRITE);
            } else {
                // forbid writing thing:/acl if the ACL entry was missing Administrate permission
                labelScoped.setRevokedPermissions(PoliciesResourceType.thingResource(Thing.JsonFields.ACL.getPointer()),
                        Permission.WRITE);
            }
        });
        return policyBuilder.build();
    }

    /**
     * Migrates the passed {@code Policy} into an {@code AccessControlList}.
     *
     * @param policy the Policy to migrate.
     * @return the AccessControlList.
     */
    public static AccessControlList policyToAccessControlList(final Policy policy) {
        final AccessControlListBuilder aclBuilder = ThingsModelFactory.newAclBuilder();
        policy.forEach(policyEntry -> {
            final List<AuthorizationSubject> aclSubjects = new ArrayList<>();
            policyEntry.getSubjects().stream()
                    .filter(policySubject -> policySubject.getType() == SubjectType.JWT
                            || policySubject.getType() == SubjectType.JWT
                            || policySubject.getType() == SubjectType.JWT
                            || policySubject.getType() == SubjectType.JWT)
                    .forEach(policySubject ->
                            aclSubjects.add(AuthorizationSubject.newInstance(policySubject.getId())));

            final Optional<Resource> policyResource =
                    policyEntry.getResources()
                            .getResource(PoliciesResourceType.thingResource("_policy"));

            policyEntry.getResources().getResource(PoliciesResourceType.thingResource(ROOT_PATH)).ifPresent(root -> {
                final Permissions grantedPermissions = root.getEffectedPermissions().getGrantedPermissions();
                final Set<org.eclipse.ditto.model.things.Permission> aclPermissions = new HashSet<>();

                if (grantedPermissions.contains(Permission.READ)) {
                    aclPermissions.add(org.eclipse.ditto.model.things.Permission.READ);
                }
                if (grantedPermissions.contains(Permission.WRITE)) {
                    aclPermissions.add(org.eclipse.ditto.model.things.Permission.WRITE);

                    final boolean writePolicyAllowed =
                            !policyResource.isPresent() ||
                                    !policyResource.get().getEffectedPermissions().getRevokedPermissions()
                                            .contains(Permission.WRITE);

                    if (writePolicyAllowed) {
                        // also add "old ADMINISTRATE" as no revoke on "/policy" was present:
                        aclPermissions.add(org.eclipse.ditto.model.things.Permission.ADMINISTRATE);
                    }
                }

                aclSubjects
                        .forEach(authSubject -> aclBuilder.set(
                                ThingsModelFactory.newAclEntry(authSubject, aclPermissions)));
            });
        });
        return aclBuilder.build();
    }
}
