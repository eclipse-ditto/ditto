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

import java.util.Arrays;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError();
    }

    /**
     * Policy related test constants.
     */
    public static final class Policy {

        /**
         * The known "read" permission.
         */
        public static final String PERMISSION_READ = "READ";

        /**
         * The known "write" permission.
         */
        public static final String PERMISSION_WRITE = "WRITE";

        /**
         * A known {@code Label} for a {@code PolicyEntry}.
         */
        public static final Label LABEL = PoliciesModelFactory.newLabel("myLabel");

        /**
         * A known {@code SubjectIssuer} for a {@code PolicyEntry}.
         */
        public static final SubjectIssuer
                SUBJECT_ISSUER = PoliciesModelFactory.newSubjectIssuer("mySubjectIssuer");

        /**
         * A known {@code SubjectType} for a {@code PolicyEntry}.
         */
        public static final SubjectType
                SUBJECT_TYPE = PoliciesModelFactory.newSubjectType("mySubjectType");

        /**
         * A known {@code SubjectId} for a {@code PolicyEntry}.
         */
        public static final SubjectId
                SUBJECT_ID = PoliciesModelFactory.newSubjectId(SUBJECT_ISSUER, "mySubject");

        /**
         * A known {@code Subject} for a {@code PolicyEntry}.
         */
        public static final Subject SUBJECT =
                PoliciesModelFactory.newSubject(SUBJECT_ID, SUBJECT_TYPE);

        /**
         * Known {@code Subjects} for a {@code PolicyEntry}.
         */
        public static final Subjects SUBJECTS = PoliciesModelFactory.newSubjects(SUBJECT);

        /**
         * Known {@code EffectedPermissions} for a {@code PolicyEntry}.
         */
        public static final EffectedPermissions EFFECTED_PERMISSIONS = PoliciesModelFactory
                .newEffectedPermissions(Arrays.asList("READ", "WRITE"),
                        PoliciesModelFactory.noPermissions());

        public static final String RESOURCE_TYPE = "thing";

        /**
         * A known {@code JsonPointer} for a {@code Resource}.
         */
        public static final JsonPointer RESOURCE_PATH = JsonFactory.newPointer("/attributes/foo");

        /**
         * A known {@code Resource} for a {@code PolicyEntry}.
         */
        public static final Resource RESOURCE = PoliciesModelFactory.newResource(RESOURCE_TYPE, RESOURCE_PATH,
                EFFECTED_PERMISSIONS);

        /**
         * Known {@code Resources} for a {@code PolicyEntry}.
         */
        public static final Resources RESOURCES = PoliciesModelFactory.newResources(RESOURCE);

        /**
         * A known {@code PolicyEntry} for a {@code Policy}.
         */
        public static final PolicyEntry POLICY_ENTRY = PoliciesModelFactory.newPolicyEntry(LABEL, SUBJECTS, RESOURCES, ImportableType.NEVER);

        /**
         * A known identifier for a {@code Policy}.
         */
        public static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto.example","myPolicy");

        /**
         * A known revision number of a Policy.
         */
        public static final long REVISION_NUMBER = 1337;

        /**
         * A known revision of a Policy.
         */
        public static final PolicyRevision REVISION = PoliciesModelFactory.newPolicyRevision(REVISION_NUMBER);

        /**
         * A known {@code Policy}.
         */
        public static final org.eclipse.ditto.policies.model.Policy POLICY =
                PoliciesModelFactory.newPolicyBuilder(POLICY_ID) //
                        .set(POLICY_ENTRY) //
                        .setRevision(REVISION) //
                        .build();

        private Policy() {
            throw new AssertionError();
        }
    }

}
