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
package org.eclipse.ditto.policies.service.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError();
    }

    /**
     * Policy-related test constants.
     */
    public static final class Policy {

        /**
         * A known SubjectType.
         */
        public static final SubjectType SUBJECT_TYPE = SubjectType.newInstance("mySubjectType");

        /**
         * The known "read" permission.
         */
        public static final String PERMISSION_READ = "READ";

        /**
         * The known "write" permission.
         */
        public static final String PERMISSION_WRITE = "WRITE";


        /**
         * All known permissions.
         */
        public static final Iterable<String> PERMISSIONS_ALL = Arrays.asList(PERMISSION_READ, PERMISSION_WRITE);

        public static final EffectedPermissions READ_GRANTED =
                EffectedPermissions.newInstance(Collections.singleton(PERMISSION_READ), Collections.emptySet());
        public static final EffectedPermissions READ_WRITE_REVOKED =
                EffectedPermissions.newInstance(Collections.emptySet(),
                        Arrays.asList(PERMISSION_READ, PERMISSION_WRITE));
        public static final ResourceKey FEATURES_RESOURCE_KEY = ResourceKey.newInstance("thing", "/features");
        public static final ResourceKey NEW_ATTRIBUTE_RESOURCE_KEY = ResourceKey.newInstance("thing",
                "/attribute/new");
        public static final Resource NEW_ATTRIBUTE_RESOURCE = Resource.newInstance(NEW_ATTRIBUTE_RESOURCE_KEY,
                READ_GRANTED);
        public static final Resource FEATURES_RESOURCE =
                Resource.newInstance(FEATURES_RESOURCE_KEY, READ_WRITE_REVOKED);
        public static final Resource
                MODIFIED_FEATURES_RESOURCE = Resource.newInstance(FEATURES_RESOURCE_KEY, READ_GRANTED);
        public static final Label SUPPORT_LABEL = Label.of("Support");
        public static final SubjectId
                SUPPORT_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, UUID.randomUUID().toString());
        public static final Subject SUPPORT_SUBJECT = Subject.newInstance(SUPPORT_SUBJECT_ID);

        public static final SubjectId ADDITIONAL_SUPPORT_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE,
                UUID.randomUUID().toString());
        public static final Subject ADDITIONAL_SUPPORT_SUBJECT = Subject.newInstance(ADDITIONAL_SUPPORT_SUBJECT_ID);
        public static final Subject SUPPORT_SUBJECT_WITH_EXPIRY = PoliciesModelFactory.newSubject(
                SUPPORT_SUBJECT_ID,
                SubjectType.UNKNOWN,
                SubjectExpiry.newInstance(Instant.now().plus(Duration.ofDays(1L)))
        );

        /**
         * A known identifier for a {@code Policy}.
         */
        public static final PolicyId POLICY_IMPORT_ID = PolicyId.of("org.eclipse.ditto.example", "importedPolicy");

        /**
         * A known {@code PolicyImport} for a {@code Policy}.
         */
        public static final PolicyImport POLICY_IMPORT = PolicyImport.newInstance(POLICY_IMPORT_ID, EffectedImports.newInstance(null));


        /**
         * A known {@code PolicyImport} for a {@code Policy}.
         */
        public static final PolicyImport POLICY_IMPORT_WITH_ENTRIES = PolicyImport.newInstance(POLICY_IMPORT_ID,
                EffectedImports.newInstance(List.of(Label.of("IncludedLabel"))));

        /**
         * A known identifier for a {@code Policy}.
         */
        public static final PolicyId ADDITIONAL_POLICY_IMPORT_ID = PolicyId.of("org.eclipse.ditto.example", "additionalImportedPolicy");

        /**
         * A known {@code PolicyImport} for a {@code Policy}.
         */
        public static final PolicyImport ADDITIONAL_POLICY_IMPORT = PolicyImport.newInstance(ADDITIONAL_POLICY_IMPORT_ID, EffectedImports.newInstance(null));

        /**
         * A known {@code PolicyImports} for a {@code Policy}.
         */
        public static final PolicyImports ADDITIONAL_POLICY_IMPORTS = PolicyImports.newInstance(ADDITIONAL_POLICY_IMPORT);

        /**
         * A known {@code PolicyImport} for a {@code Policy}.
         */
        public static final PolicyImport ADDITIONAL_POLICY_IMPORT_WITH_ENTRIES = PolicyImport.newInstance(ADDITIONAL_POLICY_IMPORT_ID,
                EffectedImports.newInstance(List.of(Label.of("OtherIncludedLabel"))));

        /**
         * A known {@code PolicyImports} for a {@code Policy}.
         */
        public static final PolicyImports
                POLICY_IMPORTS = PolicyImports.newInstance(POLICY_IMPORT, ADDITIONAL_POLICY_IMPORT);

        /**
         * A known {@code PolicyImports} for a {@code Policy}.
         */
        public static final PolicyImports POLICY_IMPORTS_WITH_ENTRIES = PolicyImports.newInstance(POLICY_IMPORT_WITH_ENTRIES, ADDITIONAL_POLICY_IMPORT_WITH_ENTRIES);


        public static org.eclipse.ditto.policies.model.Policy policyWithRandomName() {
            return PoliciesModelFactory.newPolicyBuilder(PolicyId.inNamespaceWithRandomName("test"))
                    .forLabel("EndUser")
                    .setSubject(SubjectIssuer.GOOGLE, UUID.randomUUID().toString(), SubjectType.newInstance("type"))
                    .setGrantedPermissions("thing", "/", PERMISSION_READ, PERMISSION_WRITE)
                    .setRevokedPermissions("thing", "/attributes", PERMISSION_WRITE)
                    .forLabel(SUPPORT_LABEL)
                    .setSubject(SUPPORT_SUBJECT)
                    .setRevokedPermissions(FEATURES_RESOURCE_KEY, PERMISSION_READ, PERMISSION_WRITE)
                    .setModified(Instant.now())
                    .setPolicyImports(POLICY_IMPORTS_WITH_ENTRIES)
                    .build();
        }

        public static PolicyEntry policyEntryWithLabel(final String label) {
            return PoliciesModelFactory.newPolicyEntry(label,
                    PoliciesModelFactory.newSubjects(Subject.newInstance(SubjectIssuer.GOOGLE,
                            UUID.randomUUID().toString())),
                    PoliciesModelFactory.newResources(Resource.newInstance(ResourceKey.newInstance("thing",
                            "/attributes/custom"),
                            PoliciesModelFactory.newEffectedPermissions(Collections.singleton(PERMISSION_READ),
                                    Collections.singleton(PERMISSION_WRITE))))
            );
        }

        /**
         * The known Policy ID.
         */
        public static final PolicyId POLICY_ID = PolicyId.of("com.example", "testPolicy");

        public static final Label LABEL = Label.of("custom");

        public static final String RESOURCE_TYPE_POLICY = "policy";

        private static final String RESOURCE_TYPE_THING = "thing";

        private static final JsonPointer RESOURCE_PATH = JsonPointer.of("/foo/bar");

        /**
         * A Policy to be used in persistence tests.
         */
        public static final org.eclipse.ditto.policies.model.Policy POLICY =
                PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                        .setSubjectFor(LABEL, SUPPORT_SUBJECT_ID, SUBJECT_TYPE)
                        .setGrantedPermissionsFor(LABEL, RESOURCE_TYPE_POLICY, "/", PERMISSION_READ, PERMISSION_WRITE)
                        .setGrantedPermissionsFor(LABEL, RESOURCE_TYPE_THING, "/", PERMISSION_READ, PERMISSION_WRITE)
                        .setRevokedPermissionsFor(LABEL, RESOURCE_TYPE_THING, RESOURCE_PATH, PERMISSION_WRITE)
                        .setPolicyImports(POLICY_IMPORTS_WITH_ENTRIES)
                        .build();

        public static PolicyImport policyImportWithId(final String importedPolicyId) {
            return PolicyImport.newInstance(PolicyId.of("com.example", importedPolicyId), EffectedImports.newInstance(null));
        }

        private Policy() {
            throw new AssertionError();
        }
    }

}
