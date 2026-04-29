/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.PolicyEntryInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryReferenceConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportReferenceConflictException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryReferences;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests reference validation across all policy modification strategies.
 */
public final class ReferencesValidationStrategyTest extends AbstractPolicyCommandStrategyTest {

    private static final PolicyId POLICY_ID = TestConstants.Policy.POLICY_ID;
    private static final PolicyId IMPORTED_POLICY_ID = TestConstants.Policy.POLICY_IMPORT_ID;
    private static final DittoHeaders HEADERS = DittoHeaders.empty();

    private DeletePolicyImportStrategy deletePolicyImportStrategy;
    private DeletePolicyImportsStrategy deletePolicyImportsStrategy;
    private ModifyPolicyImportsStrategy modifyPolicyImportsStrategy;
    private DeletePolicyEntryStrategy deletePolicyEntryStrategy;
    private ModifyPolicyEntryReferencesStrategy modifyPolicyEntryReferencesStrategy;
    private CreatePolicyStrategy createPolicyStrategy;
    private ModifyPolicyStrategy modifyPolicyStrategy;
    private ModifyPolicyEntriesStrategy modifyPolicyEntriesStrategy;
    private ModifyPolicyEntryStrategy modifyPolicyEntryStrategy;

    @Before
    public void setUp() {
        final var config = DefaultPolicyConfig.of(ConfigFactory.load("policy-test"));
        deletePolicyImportStrategy = new DeletePolicyImportStrategy(config);
        deletePolicyImportsStrategy = new DeletePolicyImportsStrategy(config);
        modifyPolicyImportsStrategy = new ModifyPolicyImportsStrategy(config);
        deletePolicyEntryStrategy = new DeletePolicyEntryStrategy(config);
        modifyPolicyEntryReferencesStrategy = new ModifyPolicyEntryReferencesStrategy(config);
        createPolicyStrategy = new CreatePolicyStrategy(config);
        modifyPolicyStrategy = new ModifyPolicyStrategy(config);
        modifyPolicyEntriesStrategy = new ModifyPolicyEntriesStrategy(config);
        modifyPolicyEntryStrategy = new ModifyPolicyEntryStrategy(config);
    }

    // -- Helper to build policies with references --

    private static Policy policyWithImportReference() {
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .setSubjectFor("driver", Subject.newInstance(SubjectIssuer.GOOGLE, "driver"))
                .setGrantedPermissionsFor("driver", "thing", "/", "READ")
                .setReferencesFor("driver", List.of(
                        PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, Label.of("driver"))))
                .setPolicyImports(PolicyImports.newInstance(
                        PolicyImport.newInstance(IMPORTED_POLICY_ID, null)))
                .build();
    }

    private static Policy policyWithLocalReference() {
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .setSubjectFor("shared-subjects", Subject.newInstance(SubjectIssuer.GOOGLE, "operator"))
                .setGrantedPermissionsFor("shared-subjects", "thing", "/", "READ")
                .setSubjectFor("reactor-op", Subject.newInstance(SubjectIssuer.GOOGLE, "reactor-user"))
                .setGrantedPermissionsFor("reactor-op", "thing", "/", "READ")
                .setReferencesFor("reactor-op", List.of(
                        PoliciesModelFactory.newLocalEntryReference(Label.of("shared-subjects"))))
                .setPolicyImports(PoliciesModelFactory.newPolicyImports(java.util.Collections.emptyList()))
                .build();
    }

    // -- DeletePolicyImport: reject when entries reference the import --

    @Test
    public void deletePolicyImportRejectsWhenEntryReferencesImport() {
        final Policy policy = policyWithImportReference();
        final var command = DeletePolicyImport.of(POLICY_ID, IMPORTED_POLICY_ID, HEADERS);

        assertErrorResult(deletePolicyImportStrategy, policy, command,
                PolicyImportReferenceConflictException.newBuilder(POLICY_ID, IMPORTED_POLICY_ID)
                        .build());
    }

    // -- DeletePolicyImports: reject when any entry has import references --

    @Test
    public void deletePolicyImportsRejectsWhenEntriesHaveImportReferences() {
        final Policy policy = policyWithImportReference();
        final var command = DeletePolicyImports.of(POLICY_ID, HEADERS);

        assertErrorResult(deletePolicyImportsStrategy, policy, command,
                PolicyImportReferenceConflictException.newBuilderForAll(POLICY_ID)
                        .build());
    }

    // -- ModifyPolicyImports: reject when removing an import still referenced --

    @Test
    public void modifyPolicyImportsRejectsWhenRemovingReferencedImport() {
        final Policy policy = policyWithImportReference();
        // Replace imports with an empty set — the referenced import is removed
        final var command = ModifyPolicyImports.of(POLICY_ID, PoliciesModelFactory.newPolicyImports(java.util.Collections.emptyList()), HEADERS);

        assertErrorResult(modifyPolicyImportsStrategy, policy, command,
                PolicyImportReferenceConflictException.newBuilder(POLICY_ID, IMPORTED_POLICY_ID)
                        .build());
    }

    // -- DeletePolicyEntry: reject when another entry has a local reference to it --

    @Test
    public void deletePolicyEntryRejectsWhenLocallyReferenced() {
        final Policy policy = policyWithLocalReference();
        final var command = DeletePolicyEntry.of(POLICY_ID, Label.of("shared-subjects"), HEADERS);

        assertErrorResult(deletePolicyEntryStrategy, policy, command,
                PolicyEntryReferenceConflictException.newBuilder(POLICY_ID,
                                Label.of("shared-subjects"), Label.of("reactor-op"))
                        .build());
    }

    @Test
    public void deletePolicyEntrySucceedsWhenNotReferenced() {
        final Policy policy = policyWithLocalReference();
        // Deleting "reactor-op" is fine — nobody references it
        final var command = DeletePolicyEntry.of(POLICY_ID, Label.of("reactor-op"), HEADERS);

        // Should not throw a reference conflict (may fail with policy invalid due to no subjects, but not
        // a reference conflict)
        final Dummy<?> mock = Dummy.mock();
        applyStrategy(deletePolicyEntryStrategy, getDefaultContext(), policy, command).accept(cast(mock), null);
        verify(mock).onMutation(any(), any(), any(), any(boolean.class), any(boolean.class), eq(null));
    }

    // -- ModifyPolicyEntryReferences: reject when local reference target doesn't exist --

    @Test
    public void modifyPolicyEntryReferencesRejectsInvalidLocalReference() {
        final Policy policy = policyWithLocalReference();
        final var command = ModifyPolicyEntryReferences.of(POLICY_ID, Label.of("reactor-op"),
                List.of(PoliciesModelFactory.newLocalEntryReference(Label.of("nonexistent"))),
                HEADERS);

        assertErrorResult(modifyPolicyEntryReferencesStrategy, policy, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'reactor-op' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Local reference targets entry 'nonexistent' which does not exist in the policy.")
                        .build());
    }

    @Test
    public void modifyPolicyEntryReferencesAcceptsValidLocalReference() {
        final Policy policy = policyWithLocalReference();
        final var command = ModifyPolicyEntryReferences.of(POLICY_ID, Label.of("reactor-op"),
                List.of(PoliciesModelFactory.newLocalEntryReference(Label.of("shared-subjects"))),
                HEADERS);

        final Dummy<?> mock = Dummy.mock();
        applyStrategy(modifyPolicyEntryReferencesStrategy, getDefaultContext(), policy, command)
                .accept(cast(mock), null);
        verify(mock).onMutation(any(), any(), any(), any(boolean.class), any(boolean.class), eq(null));
    }

    // -- Self-reference rejection --

    @Test
    public void modifyPolicyEntryReferencesRejectsSelfReference() {
        final Policy policy = policyWithLocalReference();
        final var command = ModifyPolicyEntryReferences.of(POLICY_ID, Label.of("reactor-op"),
                List.of(PoliciesModelFactory.newLocalEntryReference(Label.of("reactor-op"))),
                HEADERS);

        assertErrorResult(modifyPolicyEntryReferencesStrategy, policy, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'reactor-op' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Entry must not reference itself.")
                        .build());
    }

    @Test
    public void createPolicyRejectsSelfReference() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .setSubjectFor("self-ref", Subject.newInstance(SubjectIssuer.GOOGLE, "user"))
                .setGrantedPermissionsFor("self-ref", "thing", "/", "READ")
                .setReferencesFor("self-ref", List.of(
                        PoliciesModelFactory.newLocalEntryReference(Label.of("self-ref"))))
                .build();

        final var command = CreatePolicy.of(policy, HEADERS);

        assertErrorResult(createPolicyStrategy, null, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'self-ref' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Entry must not reference itself.")
                        .build());
    }

    // -- CreatePolicy: reject when references are invalid --

    @Test
    public void createPolicyRejectsInvalidLocalReference() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .setSubjectFor("entry-with-ref", Subject.newInstance(SubjectIssuer.GOOGLE, "user"))
                .setGrantedPermissionsFor("entry-with-ref", "thing", "/", "READ")
                .setReferencesFor("entry-with-ref", List.of(
                        PoliciesModelFactory.newLocalEntryReference(Label.of("nonexistent"))))
                .build();

        final var command = CreatePolicy.of(policy, HEADERS);

        assertErrorResult(createPolicyStrategy, null, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'entry-with-ref' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Local reference targets entry 'nonexistent' which does not exist in the policy.")
                        .build());
    }

    @Test
    public void createPolicyRejectsImportReferenceToUndeclaredImport() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .setSubjectFor("entry-with-ref", Subject.newInstance(SubjectIssuer.GOOGLE, "user"))
                .setGrantedPermissionsFor("entry-with-ref", "thing", "/", "READ")
                .setReferencesFor("entry-with-ref", List.of(
                        PoliciesModelFactory.newEntryReference(
                                PolicyId.of("com.acme:not-imported"), Label.of("role"))))
                .build();

        final var command = CreatePolicy.of(policy, HEADERS);

        assertErrorResult(createPolicyStrategy, null, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'entry-with-ref' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Import reference targets policy 'com.acme:not-imported' which is not declared in imports.")
                        .build());
    }

    // -- ModifyPolicy: reject when references are invalid --

    @Test
    public void modifyPolicyRejectsInvalidLocalReference() {
        final Policy existingPolicy = TestConstants.Policy.POLICY;
        final Policy replacement = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .setSubjectFor("entry-with-ref", Subject.newInstance(SubjectIssuer.GOOGLE, "user"))
                .setGrantedPermissionsFor("entry-with-ref", "thing", "/", "READ")
                .setReferencesFor("entry-with-ref", List.of(
                        PoliciesModelFactory.newLocalEntryReference(Label.of("nonexistent"))))
                .build();

        final var command = ModifyPolicy.of(POLICY_ID, replacement, HEADERS);

        assertErrorResult(modifyPolicyStrategy, existingPolicy, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'entry-with-ref' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Local reference targets entry 'nonexistent' which does not exist in the policy.")
                        .build());
    }

    // -- ModifyPolicyEntries: reject when references are invalid --

    @Test
    public void modifyPolicyEntriesRejectsInvalidLocalReference() {
        final Policy existingPolicy = TestConstants.Policy.POLICY;
        final Set<PolicyEntry> entries = Set.of(
                PoliciesModelFactory.newPolicyEntry("admin",
                        PoliciesModelFactory.newSubjects(Subject.newInstance(SubjectIssuer.GOOGLE, "admin")),
                        PoliciesModelFactory.newResources(
                                PoliciesModelFactory.newResource("policy", "/",
                                        PoliciesModelFactory.newEffectedPermissions(
                                                Set.of("READ", "WRITE"), Set.of())))),
                PoliciesModelFactory.newPolicyEntry("entry-with-ref",
                        PoliciesModelFactory.newSubjects(Subject.newInstance(SubjectIssuer.GOOGLE, "user")),
                        PoliciesModelFactory.newResources(
                                PoliciesModelFactory.newResource("thing", "/",
                                        PoliciesModelFactory.newEffectedPermissions(
                                                Set.of("READ"), Set.of()))),
                        null, ImportableType.IMPLICIT, null,
                        List.of(PoliciesModelFactory.newLocalEntryReference(Label.of("nonexistent"))))
        );

        final var command = ModifyPolicyEntries.of(POLICY_ID, entries, HEADERS);

        assertErrorResult(modifyPolicyEntriesStrategy, existingPolicy, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'entry-with-ref' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Local reference targets entry 'nonexistent' which does not exist in the policy.")
                        .build());
    }

    // -- ModifyPolicyEntry: reject when the new entry has invalid references --

    @Test
    public void modifyPolicyEntryRejectsInvalidLocalReference() {
        final Policy existingPolicy = TestConstants.Policy.POLICY;
        final PolicyEntry entryWithBadRef = PoliciesModelFactory.newPolicyEntry("new-entry",
                PoliciesModelFactory.newSubjects(Subject.newInstance(SubjectIssuer.GOOGLE, "user")),
                PoliciesModelFactory.newResources(
                        PoliciesModelFactory.newResource("thing", "/",
                                PoliciesModelFactory.newEffectedPermissions(
                                        Set.of("READ"), Set.of()))),
                null, ImportableType.IMPLICIT, null,
                List.of(PoliciesModelFactory.newLocalEntryReference(Label.of("nonexistent"))));

        final var command = ModifyPolicyEntry.of(POLICY_ID, entryWithBadRef, HEADERS);

        assertErrorResult(modifyPolicyEntryStrategy, existingPolicy, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'new-entry' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Local reference targets entry 'nonexistent' which does not exist in the policy.")
                        .build());
    }

    @Test
    public void modifyPolicyEntryRejectsImportReferenceToUndeclaredImport() {
        final Policy existingPolicy = TestConstants.Policy.POLICY;
        final PolicyEntry entryWithBadRef = PoliciesModelFactory.newPolicyEntry("new-entry",
                PoliciesModelFactory.newSubjects(Subject.newInstance(SubjectIssuer.GOOGLE, "user")),
                PoliciesModelFactory.newResources(
                        PoliciesModelFactory.newResource("thing", "/",
                                PoliciesModelFactory.newEffectedPermissions(
                                        Set.of("READ"), Set.of()))),
                null, ImportableType.IMPLICIT, null,
                List.of(PoliciesModelFactory.newEntryReference(
                        PolicyId.of("com.acme", "not-imported"), Label.of("role"))));

        final var command = ModifyPolicyEntry.of(POLICY_ID, entryWithBadRef, HEADERS);

        assertErrorResult(modifyPolicyEntryStrategy, existingPolicy, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'new-entry' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Import reference targets policy 'com.acme:not-imported' which is not declared in imports.")
                        .build());
    }

    // -- ModifyPolicyEntryReferences: reject local refs to importable=never --

    @Test
    public void modifyPolicyEntryReferencesRejectsLocalReferenceToNeverEntry() {
        // Build a policy where 'breakGlass' is importable=never; another entry tries to ref it locally.
        final Policy policyWithNever = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .build()
                .toBuilder()
                .set(PoliciesModelFactory.newPolicyEntry(Label.of("breakGlass"),
                        PoliciesModelFactory.newSubjects(
                                Subject.newInstance(SubjectIssuer.GOOGLE, "rootAdmin")),
                        PoliciesModelFactory.emptyResources(),
                        null, ImportableType.NEVER, null, null))
                .set(PoliciesModelFactory.newPolicyEntry(Label.of("user"),
                        PoliciesModelFactory.newSubjects(
                                Subject.newInstance(SubjectIssuer.GOOGLE, "alice")),
                        PoliciesModelFactory.emptyResources(),
                        null, ImportableType.IMPLICIT, null, null))
                .build();

        final var command = ModifyPolicyEntryReferences.of(POLICY_ID, Label.of("user"),
                List.of(PoliciesModelFactory.newLocalEntryReference(Label.of("breakGlass"))),
                HEADERS);

        assertErrorResult(modifyPolicyEntryReferencesStrategy, policyWithNever, command,
                PolicyEntryInvalidException.newBuilder()
                        .message("The references of PolicyEntry 'user' on Policy '" + POLICY_ID + "' are invalid.")
                        .description("Local reference targets entry 'breakGlass' which is marked importable=never " +
                                "and cannot be referenced.")
                        .build());
    }

    // -- Duplicate-detection key uses structured pair, not string concat --
    // (M3) A local label literally containing a colon must NOT collide with an import-ref of
    // the same shape after concat.

    @Test
    public void modifyPolicyEntryReferencesAcceptsCollidingShapedLocalAndImportRefs() {
        // Local label "com.example:templateA:driver" should not collide with import-ref to
        // policy "com.example:templateA" entry "driver" — the dedup key must distinguish them.
        final Label collidingLocal = Label.of("com.example:templateA:driver");
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .set(PoliciesModelFactory.newPolicyEntry(collidingLocal,
                        PoliciesModelFactory.newSubjects(
                                Subject.newInstance(SubjectIssuer.GOOGLE, "anyone")),
                        PoliciesModelFactory.emptyResources(),
                        null, ImportableType.IMPLICIT, null, null))
                .setSubjectFor("driver", Subject.newInstance(SubjectIssuer.GOOGLE, "drv"))
                .setGrantedPermissionsFor("driver", "thing", "/", "READ")
                .setPolicyImports(PolicyImports.newInstance(
                        PolicyImport.newInstance(PolicyId.of("com.example", "templateA"), null)))
                .build();

        // Both refs have refKey="com.example:templateA:driver" under naive string concat → false collision.
        // Under structured EntryReference equality, they're distinct.
        final var command = ModifyPolicyEntryReferences.of(POLICY_ID, Label.of("driver"),
                List.of(
                        PoliciesModelFactory.newLocalEntryReference(collidingLocal),
                        PoliciesModelFactory.newEntryReference(
                                PolicyId.of("com.example", "templateA"), Label.of("driver"))),
                HEADERS);

        // Should succeed — two distinct references, no false duplicate.
        final Dummy<?> mock = Dummy.mock();
        applyStrategy(modifyPolicyEntryReferencesStrategy, getDefaultContext(), policy, command)
                .accept(cast(mock), null);
        verify(mock).onMutation(any(), any(), any(), any(boolean.class), any(boolean.class), eq(null));
    }

    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    private static <T extends org.eclipse.ditto.base.model.signals.events.Event<?>> Dummy<T> cast(
            final Dummy<?> dummy) {
        return (Dummy) dummy;
    }
}
