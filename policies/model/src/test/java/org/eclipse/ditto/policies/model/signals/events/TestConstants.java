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
package org.eclipse.ditto.policies.model.signals.events;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;

/**
 * Defines constants for testing.
 */
final class TestConstants {

    /**
     * A known correlation id for testing.
     */
    public static final String CORRELATION_ID = "a780b7b5-fdd2-4864-91fc-80df6bb0a636";

    /**
     * Known command headers.
     */
    public static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance("the_subject"),
                    AuthorizationSubject.newInstance("another_subject")))
            .build();

    /**
     * Empty command headers.
     */
    public static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /**
     * A known timestamp.
     */
    public static final Instant TIMESTAMP = Instant.EPOCH;

    /**
     * A known metadata.
     */
    public static final Metadata METADATA = Metadata.newBuilder()
            .set("creator", "The epic Ditto team")
            .build();

    /**
     * Known JSON parse options.
     */
    public static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();


    private TestConstants() {
        throw new AssertionError();
    }

    /**
     * Policy related test constants.
     */
    public static final class Policy {

        /**
         * A known {@code Label} for a {@code PolicyEntry}.
         */
        public static final Label LABEL = PoliciesModelFactory.newLabel("myLabel");

        /**
         * A known {@code SubjectId} for a {@code PolicyEntry}.
         */
        public static final SubjectId SUBJECT_ID =
                PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "mySubject");

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
        public static final EffectedPermissions EFFECTED_PERMISSIONS =
                PoliciesModelFactory.newEffectedPermissions(Arrays.asList("READ", "WRITE"),
                        PoliciesModelFactory.noPermissions());

        /**
         * A known type for a {@code Resource}.
         */
        public static final String RESOURCE_TYPE = "thing";

        /**
         * A known {@code JsonPointer} for a {@code Resource}.
         */
        public static final JsonPointer RESOURCE_PATH = JsonFactory.newPointer("/attributes/foo");

        /**
         * A known full qualified path for a {@code Resource}.
         */
        public static final ResourceKey RESOURCE_KEY = ResourceKey.newInstance(RESOURCE_TYPE, RESOURCE_PATH);

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
         * known {@code PolicyEntry}s for a {@code Policy}.
         */
        public static final Iterable<PolicyEntry> POLICY_ENTRIES =
                new HashSet<>(Arrays.asList(PoliciesModelFactory.newPolicyEntry(LABEL, SUBJECTS, RESOURCES),
                        PoliciesModelFactory.newPolicyEntry(Label.of("foo"), SUBJECTS, RESOURCES),
                        PoliciesModelFactory.newPolicyEntry(Label.of("bar"), SUBJECTS, RESOURCES)));

        /**
         * A known identifier for a {@code Policy}.
         */
        public static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto.example", "myPolicy");

        /**
         * A known identifier for a {@code Policy}.
         */
        public static final PolicyId IMPORTED_POLICY_ID = PolicyId.of("org.eclipse.ditto.example", "mySupportPolicy");

        /**
         * A known {@code PolicyImport} for a {@code Policy}.
         */
        public static final PolicyImport
                POLICY_IMPORT = PolicyImport.newInstance(IMPORTED_POLICY_ID, EffectedImports.newInstance(null));

        /**
         * A known {@code PolicyImports} for a {@code Policy}.
         */
        public static final PolicyImports POLICY_IMPORTS = PolicyImports.newInstance(POLICY_IMPORT);

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
                PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                        .set(POLICY_ENTRY)
                        .build();

        private Policy() {
            throw new AssertionError();
        }

    }

}
