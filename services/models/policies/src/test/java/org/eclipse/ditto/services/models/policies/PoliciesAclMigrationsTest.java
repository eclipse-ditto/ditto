/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.models.policies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permission;
import org.junit.Test;

/**
 * Unit test for {@link PoliciesAclMigrations}.
 */
public final class PoliciesAclMigrationsTest {

    private static final EffectedPermissions READ_PERMISSIONS =
            EffectedPermissions.newInstance(Collections.singleton(Permission.READ.name()),
                    Collections.emptyList());
    private static final EffectedPermissions REVOKED_WRITE_PERMISSIONS =
            EffectedPermissions.newInstance(Collections.emptyList(), Collections.singleton(Permission.WRITE.name()));

    @Test
    public void verifyMigratedSubjects() {
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "user");
        final AccessControlList aclToMigrate = AccessControlList.newBuilder()
                .set(AclEntry.newInstance(AuthorizationSubject.newInstance(subjectId), Permission.READ))
                .build();
        final PolicyId policyId = PolicyId.dummy();
        final List<SubjectIssuer> issuers = Arrays.asList(SubjectIssuer.GOOGLE, SubjectIssuer.newInstance("any-other"));

        final String expectedLabel = "acl_" + subjectId.getSubject();
        final PolicyEntry expectedEntry = createExpectedPolicyEntry(expectedLabel, subjectId, issuers);

        final Policy migratedPolicy =
                PoliciesAclMigrations.accessControlListToPolicyEntries(aclToMigrate, policyId, issuers);

        assertThat(migratedPolicy.getEntryFor(expectedLabel)).contains(expectedEntry);
    }

    private static PolicyEntry createExpectedPolicyEntry(final String label, final SubjectId subjectId, final List<SubjectIssuer> issuers) {
        final List<Subject> expectedSubjects = issuers.stream()
                .map(issuer -> Subject.newInstance(issuer, subjectId.getSubject()))
                .collect(Collectors.toList());
        final Resources expectedResources = Resources.newInstance(
                Resource.newInstance(PoliciesResourceType.thingResource("/"), READ_PERMISSIONS),
                Resource.newInstance(PoliciesResourceType.thingResource("/acl"), REVOKED_WRITE_PERMISSIONS),
                Resource.newInstance(PoliciesResourceType.policyResource("/"), READ_PERMISSIONS),
                Resource.newInstance(PoliciesResourceType.messageResource("/"), READ_PERMISSIONS)
        );
        return PolicyEntry.newInstance(label,
                expectedSubjects,
                expectedResources);
    }
}
