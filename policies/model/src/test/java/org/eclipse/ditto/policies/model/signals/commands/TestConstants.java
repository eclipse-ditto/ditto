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
package org.eclipse.ditto.policies.model.signals.commands;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
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
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyIdNotExplicitlySettableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotCreatableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionFailedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionNotModifiedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyTooManyModifyingRequestsException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ResourceNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ResourcesNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectsNotModifiableException;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

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
        public static final EffectedPermissions EFFECTED_PERMISSIONS = PoliciesModelFactory
                .newEffectedPermissions(Arrays.asList("READ", "WRITE"), PoliciesModelFactory.noPermissions());

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
        public static final PolicyEntry POLICY_ENTRY = PoliciesModelFactory.newPolicyEntry(LABEL, SUBJECTS, RESOURCES, ImportableType.NEVER);

        /**
         * known {@code PolicyEntry}s for a {@code Policy}.
         */
        public static final Iterable<PolicyEntry> POLICY_ENTRIES =
                new HashSet<>(Arrays.asList(PoliciesModelFactory.newPolicyEntry(LABEL, SUBJECTS, RESOURCES, ImportableType.NEVER),
                        PoliciesModelFactory.newPolicyEntry(Label.of("foo"), SUBJECTS, RESOURCES, ImportableType.NEVER),
                        PoliciesModelFactory.newPolicyEntry(Label.of("bar"), SUBJECTS, RESOURCES, ImportableType.NEVER)));

        /**
         * A known identifier for a {@code Policy}.
         */
        public static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto.example", "myPolicy");

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

        /**
         * A known {@code PolicyConflictException}.
         */
        public static final PolicyConflictException POLICY_CONFLICT_EXCEPTION =
                PolicyConflictException.newBuilder(POLICY_ID).build();


        /**
         * A known {@code PolicyPreconditionFailedException}.
         */
        public static final PolicyPreconditionFailedException POLICY_PRECONDITION_FAILED_EXCEPTION =
                PolicyPreconditionFailedException
                        .newBuilder(DittoHeaderDefinition.IF_MATCH.getKey(), "\"rev:1\"", "\"rev:2\"")
                        .build();

        /**
         * A known {@code PolicyPreconditionNotModifiedException}.
         */
        public static final PolicyPreconditionNotModifiedException POLICY_PRECONDITION_NOT_MODIFIED_EXCEPTION =
                PolicyPreconditionNotModifiedException
                        .newBuilder("\"rev:1\"", "*")
                        .build();

        /**
         * A known {@code PolicyNotAccessibleException}.
         */
        public static final PolicyNotAccessibleException POLICY_NOT_ACCESSIBLE_EXCEPTION =
                PolicyNotAccessibleException.newBuilder(POLICY_ID).build();

        /**
         * A known {@code PolicyNotCreatableException}.
         */
        public static final PolicyNotCreatableException POLICY_NOT_CREATABLE_EXCEPTION =
                PolicyNotCreatableException.newBuilder(POLICY_ID).build();

        /**
         * A known {@code PolicyNotModifiableException}.
         */
        public static final PolicyNotModifiableException POLICY_NOT_MODIFIABLE_EXCEPTION =
                PolicyNotModifiableException.newBuilder(POLICY_ID).build();

        /**
         * A known {@code PolicyIdNotExplicitlySettableException}.
         */
        public static final PolicyIdNotExplicitlySettableException POLICY_ID_NOT_EXPLICITLY_SETTABLE_EXCEPTION =
                PolicyIdNotExplicitlySettableException.newBuilder().build();

        /**
         * A known {@code PolicyUnavailableException}.
         */
        public static final PolicyUnavailableException POLICY_UNAVAILABLE_EXCEPTION =
                PolicyUnavailableException.newBuilder(POLICY_ID).build();

        /**
         * A known {@code PolicyTooManyModifyingRequestsException}.
         */
        public static final PolicyTooManyModifyingRequestsException POLICY_TOO_MANY_MODIFYING_REQUESTS_EXCEPTION =
                PolicyTooManyModifyingRequestsException.newBuilder(POLICY_ID).build();

        /**
         * A known {@code PolicyEntryNotAccessibleException}.
         */
        public static final PolicyEntryNotAccessibleException POLICY_ENTRY_NOT_ACCESSIBLE_EXCEPTION =
                PolicyEntryNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString()).build();

        /**
         * A known {@code PolicyEntryNotModifiableException}.
         */
        public static final PolicyEntryNotModifiableException POLICY_ENTRY_NOT_MODIFIABLE_EXCEPTION =
                PolicyEntryNotModifiableException.newBuilder(POLICY_ID, LABEL.toString()).build();

        /**
         * A known {@code PolicyEntryModificationInvalidException}.
         */
        public static final PolicyEntryModificationInvalidException POLICY_ENTRY_MODIFICATION_INVALID_EXCEPTION =
                PolicyEntryModificationInvalidException.newBuilder(POLICY_ID, LABEL.toString()).build();

        /**
         * A known {@code PolicyModificationInvalidException}.
         */
        public static final PolicyModificationInvalidException POLICY_MODIFICATION_INVALID_EXCEPTION =
                PolicyModificationInvalidException.newBuilder(POLICY_ID).build();

        /**
         * A known {@code ResourcesNotModifiableException}.
         */
        public static final ResourcesNotModifiableException RESOURCES_NOT_MODIFIABLE_EXCEPTION =
                ResourcesNotModifiableException.newBuilder(POLICY_ID, LABEL.toString()).build();

        /**
         * A known {@code ResourceNotAccessibleException}.
         */
        public static final ResourceNotAccessibleException RESOURCE_NOT_ACCESSIBLE_EXCEPTION =
                ResourceNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString(), RESOURCE_PATH.toString())
                        .build();

        /**
         * A known {@code ResourceNotModifiableException}.
         */
        public static final ResourceNotModifiableException RESOURCE_NOT_MODIFIABLE_EXCEPTION =
                ResourceNotModifiableException.newBuilder(POLICY_ID, LABEL.toString(), RESOURCE_PATH.toString())
                        .build();

        /**
         * A known {@code SubjectsNotModifiableException}.
         */
        public static final SubjectsNotModifiableException SUBJECTS_NOT_MODIFIABLE_EXCEPTION =
                SubjectsNotModifiableException.newBuilder(POLICY_ID, LABEL.toString()).build();

        /**
         * A known {@code SubjectNotAccessibleException}.
         */
        public static final SubjectNotAccessibleException SUBJECT_NOT_ACCESSIBLE_EXCEPTION =
                SubjectNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString(), SUBJECT_ID.toString()).build();

        /**
         * A known {@code SubjectNotModifiableException}.
         */
        public static final SubjectNotModifiableException SUBJECT_NOT_MODIFIABLE_EXCEPTION =
                SubjectNotModifiableException.newBuilder(POLICY_ID, LABEL.toString(), SUBJECT_ID.toString()).build();

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
         * A {@code PolicyImports} instance with the given imported policy.
         */
        public static PolicyImports getPolicyImports(final PolicyId importedPolicyId) {
            return PolicyImports.newInstance(getPolicyImport(importedPolicyId));
        };

        /**
         * A {@code PolicyImport} instance with the given imported policy.
         */
        public static PolicyImport getPolicyImport(final PolicyId importedPolicyId) {
            return PolicyImport.newInstance(importedPolicyId, EffectedImports.newInstance(null));
        };

        private Policy() {
            throw new AssertionError();
        }
    }

}
