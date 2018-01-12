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

import java.util.Arrays;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    /**
     * Empty command headers.
     */
    public static final DittoHeaders EMPTY_HEADERS = DittoHeaders.empty();

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
        public static final SubjectIssuer SUBJECT_ISSUER = SubjectIssuer.GOOGLE;

        /**
         * A known {@code SubjectId} for a {@code PolicyEntry}.
         */
        public static final SubjectId
                SUBJECT_ID = PoliciesModelFactory.newSubjectId(SUBJECT_ISSUER, "mySubject");

        /**
         * A known {@code Subject} for a {@code PolicyEntry}.
         */
        public static final Subject SUBJECT =
                PoliciesModelFactory.newSubject(SUBJECT_ID, SubjectType.newInstance("mySubjectType"));

        /**
         * Known {@code Subjects} for a {@code PolicyEntry}.
         */
        public static final Subjects SUBJECTS = PoliciesModelFactory.newSubjects(SUBJECT);

        /**
         * Known {@code EffectedPermissions} for a {@code PolicyEntry}.
         */
        public static final EffectedPermissions EFFECTED_PERMISSIONS = PoliciesModelFactory
                .newEffectedPermissions(Arrays.asList(Permission.READ, Permission.WRITE),
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
        public static final PolicyEntry POLICY_ENTRY = PoliciesModelFactory.newPolicyEntry(LABEL, SUBJECTS, RESOURCES);

        /**
         * A known identifier for a {@code Policy}.
         */
        public static final String POLICY_ID = "org.eclipse.ditto.example:myPolicy";

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
        public static final org.eclipse.ditto.model.policies.Policy POLICY =
                PoliciesModelFactory.newPolicyBuilder(POLICY_ID) //
                        .set(POLICY_ENTRY) //
                        .setRevision(REVISION) //
                        .build();

        private Policy() {
            throw new AssertionError();
        }
    }

}
