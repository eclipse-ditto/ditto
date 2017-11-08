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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.eclipse.ditto.services.models.policies.TestConstants.Policy.PERMISSION_READ;
import static org.eclipse.ditto.services.models.policies.TestConstants.Policy.PERMISSION_WRITE;
import static org.eclipse.ditto.services.models.policies.TestConstants.Policy.SUBJECT_ISSUER;
import static org.junit.Assert.assertEquals;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
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

        assertEquals(validator.isValid(), true);
        assertEquals(validator.getReason().isPresent(), false);
    }

    @Test
    public void validatePoliciesFailure() {
        final PolicyEntry entry = createPolicyEntry(PERMISSION_READ);
        final PoliciesValidator validator =
                PoliciesValidator.newInstance(Collections.singletonList(entry));

        assertThat(validator.isValid()).isFalse();
        assertThat(validator.getReason()).isPresent();
        assertThat(validator.getReason().get()).isEqualTo(
                MessageFormat.format("It must contain at least one Subject with permission(s) <{0}> on resource <{1}>!",
                        Permissions.newInstance(PERMISSION_WRITE), PoliciesResourceType.policyResource("/")));
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
