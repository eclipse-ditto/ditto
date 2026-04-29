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
package org.eclipse.ditto.policies.api;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.eclipse.ditto.policies.api.TestConstants.Policy.PERMISSION_READ;
import static org.eclipse.ditto.policies.api.TestConstants.Policy.PERMISSION_WRITE;
import static org.eclipse.ditto.policies.api.TestConstants.Policy.SUBJECT_ISSUER;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.junit.Test;

/**
 * Test PoliciesValidator.
 */
public class PoliciesValidatorTest {

    @Test
    public void validatePoliciesSuccess() {
        final PolicyEntry entry = createPolicyEntry(PERMISSION_READ,
                PERMISSION_WRITE);
        final PoliciesValidator validator =
                PoliciesValidator.newInstance(Collections.singletonList(entry));

        assertThat(validator.isValid()).isTrue();
        assertThat(validator.getReason()).isEmpty();
    }

    @Test
    public void validatePoliciesFailure() {
        final PolicyEntry entry = createPolicyEntry(PERMISSION_READ);
        final PoliciesValidator validator =
                PoliciesValidator.newInstance(Collections.singletonList(entry));

        assertThat(validator.isValid()).isFalse();
        assertThat(validator.getReason()).isPresent();
        assertThat(validator.getReason().get())
                .isEqualTo(MessageFormat.format(
                        "It must contain at least one permanent Subject with permission(s) <{0}> on resource <{1}>!",
                        Permissions.newInstance(PERMISSION_WRITE), PoliciesResourceType.policyResource("/")));
    }

    @Test
    public void policyWithOnlyExpiringSubjectsIsNotValid() {
        final SubjectExpiry expiry = PoliciesModelFactory.newSubjectExpiry(Instant.now().plus(Duration.ofDays(1L)));
        final PolicyEntry baseEntry = createPolicyEntry(PERMISSION_READ, PERMISSION_WRITE);
        final PolicyEntry expiringEntry = PoliciesModelFactory.newPolicyEntry(baseEntry.getLabel(),
                baseEntry.getSubjects()
                        .stream()
                        .map(subject -> Subject.newInstance(subject.getId(), subject.getType(), expiry))
                        .collect(Collectors.toList()),
                baseEntry.getResources());
        final PoliciesValidator validator =
                PoliciesValidator.newInstance(Collections.singletonList(expiringEntry));

        assertThat(validator.isValid()).isFalse();
        assertThat(validator.getReason().orElseThrow()).contains("It must contain at least one permanent Subject");
    }

    @Test
    public void policySplitAcrossEntriesViaMutualLocalReferencesIsValid() {
        // The WRITE-on-policy:/ invariant is satisfied AFTER local-ref resolution but not on the
        // raw entries: entry A holds the subject, entry B holds the WRITE grant; mutual refs.
        final SubjectId subjectId = PoliciesModelFactory.newSubjectId(SUBJECT_ISSUER, "admin");
        final Subject subject = PoliciesModelFactory.newSubject(subjectId);
        final Resource policyWriteResource = PoliciesModelFactory.newResource(
                PoliciesResourceType.policyResource("/"),
                PoliciesModelFactory.newEffectedPermissions(
                        Arrays.asList(PERMISSION_READ, PERMISSION_WRITE), new ArrayList<>()));

        final PolicyEntry entryA = PoliciesModelFactory.newPolicyEntry(Label.of("A"),
                PoliciesModelFactory.newSubjects(subject),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(Label.of("B"))));
        final PolicyEntry entryB = PoliciesModelFactory.newPolicyEntry(Label.of("B"),
                PoliciesModelFactory.emptySubjects(),
                PoliciesModelFactory.newResources(policyWriteResource),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(Label.of("A"))));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("ns", "split"))
                .set(entryA).set(entryB).build();

        final PoliciesValidator validator = PoliciesValidator.newInstance(policy);

        // Without local-ref resolution this would fail. With resolution, both entries effectively
        // have the admin subject and WRITE on policy:/, so it must pass.
        assertThat(validator.isValid()).isTrue();
    }

    private PolicyEntry createPolicyEntry(final String... permissions) {
        final AuthorizationSubject authorizationSubject = AuthorizationSubject.newInstance("134233");
        final SubjectId subjectId =
                PoliciesModelFactory.newSubjectId(SUBJECT_ISSUER, authorizationSubject.getId());
        final Subject subject = PoliciesModelFactory.newSubject(subjectId);
        final Iterable<String> grantedPermissions = Arrays.asList(permissions);

        final Resource resource1 = PoliciesModelFactory.newResource(PoliciesResourceType.policyResource("/"),
                PoliciesModelFactory.newEffectedPermissions(grantedPermissions, new ArrayList<>()));

        final Resource resource2 = PoliciesModelFactory.newResource(PoliciesResourceType.thingResource("/"),
                PoliciesModelFactory.newEffectedPermissions(grantedPermissions, new ArrayList<>()));

        return PoliciesModelFactory.newPolicyEntry(PoliciesModelFactory.newLabel("DEFAULT"),
                PoliciesModelFactory.newSubjects(subject), PoliciesModelFactory.newResources(resource1, resource2));
    }
}
