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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAlias;
import org.eclipse.ditto.policies.model.SubjectAliasTarget;
import org.eclipse.ditto.policies.model.SubjectAliases;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectsResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectsResponse;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasSubjectCreated;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasSubjectDeleted;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasSubjectModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasSubjectsModified;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.typesafe.config.ConfigFactory;

/**
 * Tests for subject operations through subject aliases — verifying that
 * PUT/GET/DELETE on {@code /entries/{alias}/subjects} correctly fan out to
 * the referenced entries additions targets.
 */
public final class SubjectAliasStrategiesTest extends AbstractPolicyCommandStrategyTest {

    private static final PolicyId POLICY_ID = TestConstants.Policy.POLICY_ID;
    private static final PolicyId TEMPLATE_POLICY_ID = PolicyId.of("com.example", "template");
    private static final Label ALIAS_LABEL = Label.of("operator");
    private static final Label TARGET_LABEL_1 = Label.of("operator-reactor");
    private static final Label TARGET_LABEL_2 = Label.of("operator-turbine");
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    private ModifySubjectsStrategy modifySubjectsStrategy;
    private ModifySubjectStrategy modifySubjectStrategy;
    private DeleteSubjectStrategy deleteSubjectStrategy;
    private RetrieveSubjectsStrategy retrieveSubjectsStrategy;

    /** A policy with a subject alias "operator" pointing to two entries additions targets. */
    private Policy policyWithAlias;

    @Before
    public void setUp() {
        final var config = DefaultPolicyConfig.of(ConfigFactory.load("policy-test"));
        modifySubjectsStrategy = new ModifySubjectsStrategy(config);
        modifySubjectStrategy = new ModifySubjectStrategy(config);
        deleteSubjectStrategy = new DeleteSubjectStrategy(config);
        retrieveSubjectsStrategy = new RetrieveSubjectsStrategy(config);

        // Build a policy that:
        // 1. Has a local "admin" entry (for the WRITE permission on policy:/)
        // 2. Imports a template policy with entriesAdditions for two entries
        // 3. Has a subject alias "operator" pointing to both entries additions
        final List<SubjectAliasTarget> targets = Arrays.asList(
                PoliciesModelFactory.newSubjectAliasTarget(TEMPLATE_POLICY_ID, TARGET_LABEL_1),
                PoliciesModelFactory.newSubjectAliasTarget(TEMPLATE_POLICY_ID, TARGET_LABEL_2)
        );
        final SubjectAlias alias = PoliciesModelFactory.newSubjectAlias(ALIAS_LABEL, targets);
        final SubjectAliases aliases = PoliciesModelFactory.newSubjectAliases(Collections.singletonList(alias));

        // Create the import with entriesAdditions (initially with existing subjects)
        final Subject existingSubject = Subject.newInstance(
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "existing-user"), SubjectType.GENERATED);
        final var addition1 = PoliciesModelFactory.newEntryAddition(TARGET_LABEL_1,
                Subjects.newInstance(existingSubject), null);
        final var addition2 = PoliciesModelFactory.newEntryAddition(TARGET_LABEL_2,
                Subjects.newInstance(existingSubject), null);
        final var entriesAdditions = PoliciesModelFactory.newEntriesAdditions(Arrays.asList(addition1, addition2));
        final var effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Arrays.asList(TARGET_LABEL_1, TARGET_LABEL_2), entriesAdditions);
        final var policyImport = PoliciesModelFactory.newPolicyImport(TEMPLATE_POLICY_ID, effectedImports);
        final var policyImports = PoliciesModelFactory.newPolicyImports(Collections.singletonList(policyImport));

        policyWithAlias = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor(TestConstants.Policy.LABEL, TestConstants.Policy.SUPPORT_SUBJECT_ID,
                        TestConstants.Policy.SUBJECT_TYPE)
                .setGrantedPermissionsFor(TestConstants.Policy.LABEL, "policy", "/", "READ", "WRITE")
                .setGrantedPermissionsFor(TestConstants.Policy.LABEL, "thing", "/", "READ", "WRITE")
                .setPolicyImports(policyImports)
                .setSubjectAliases(aliases)
                .build();
    }

    // ---- ModifySubjects via alias ----

    @Test
    public void modifySubjectsViaAliasFansOutToAllTargets() {
        final Subject newSubject = Subject.newInstance(
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "homer"), SubjectType.GENERATED);
        final Subjects subjects = Subjects.newInstance(newSubject);
        final ModifySubjects command = ModifySubjects.of(POLICY_ID, ALIAS_LABEL, subjects, DITTO_HEADERS);

        assertModificationResult(modifySubjectsStrategy, policyWithAlias, command,
                SubjectAliasSubjectsModified.class,
                event -> {
                    assertThat(event.getAliasLabel()).isEqualTo(ALIAS_LABEL);
                    assertThat(event.getSubjects()).contains(newSubject);
                    assertThat(event.getTargets()).hasSize(2);
                },
                ModifySubjectsResponse.class,
                response -> {});
    }

    @Test
    public void modifySubjectsForNonExistentLabelReturnsError() {
        final Label unknownLabel = Label.of("nonexistent");
        final ModifySubjects command = ModifySubjects.of(POLICY_ID, unknownLabel,
                Subjects.newInstance(Subject.newInstance("test:sub", SubjectType.GENERATED)), DITTO_HEADERS);

        assertErrorResult(modifySubjectsStrategy, policyWithAlias, command,
                policyEntryNotFound(unknownLabel));
    }

    // ---- ModifySubject (single) via alias ----

    @Test
    public void modifySubjectViaAliasCreatesNewSubjectInAllTargets() {
        final Subject newSubject = Subject.newInstance(
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "new-user"), SubjectType.GENERATED);
        final ModifySubject command = ModifySubject.of(POLICY_ID, ALIAS_LABEL, newSubject, DITTO_HEADERS);

        // The new subject doesn't exist in the targets yet, so we expect SubjectAliasSubjectCreated
        assertModificationResult(modifySubjectStrategy, policyWithAlias, command,
                SubjectAliasSubjectCreated.class,
                event -> {
                    assertThat(event.getAliasLabel()).isEqualTo(ALIAS_LABEL);
                    assertThat(event.getSubject()).isEqualTo(newSubject);
                    assertThat(event.getTargets()).hasSize(2);
                },
                ModifySubjectResponse.class,
                response -> {});
    }

    // ---- RetrieveSubjects via alias ----

    @Test
    public void retrieveSubjectsViaAliasReturnsFirstTargetSubjects() {
        final RetrieveSubjects command = RetrieveSubjects.of(POLICY_ID, ALIAS_LABEL, DITTO_HEADERS);

        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Result<?> result = applyStrategy(retrieveSubjectsStrategy, context, policyWithAlias, command);

        // Verify it returns a query result (not an error)
        final Dummy<?> mock = Dummy.mock();
        result.accept(cast(mock), null);

        final ArgumentCaptor<WithDittoHeaders> responseCaptor = ArgumentCaptor.forClass(WithDittoHeaders.class);
        verify(mock).onQuery(any(), responseCaptor.capture());

        final WithDittoHeaders response = responseCaptor.getValue();
        assertThat(response).isInstanceOf(RetrieveSubjectsResponse.class);
        final RetrieveSubjectsResponse retrieveResponse = (RetrieveSubjectsResponse) response;
        // Should contain the existing subject from the first target's entriesAdditions
        assertThat(retrieveResponse.getSubjects()).isNotEmpty();
        assertThat(retrieveResponse.getSubjects().getSubject(
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "existing-user"))).isPresent();
    }

    // ---- DeleteSubject via alias ----

    @Test
    public void deleteSubjectViaAliasFansOutToAllTargets() {
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "existing-user");
        final DeleteSubject command = DeleteSubject.of(POLICY_ID, ALIAS_LABEL, subjectId, DITTO_HEADERS);

        assertModificationResult(deleteSubjectStrategy, policyWithAlias, command,
                SubjectAliasSubjectDeleted.class,
                event -> {
                    assertThat(event.getAliasLabel()).isEqualTo(ALIAS_LABEL);
                    assertThat(event.getSubjectId()).isEqualTo(subjectId);
                    assertThat(event.getTargets()).hasSize(2);
                },
                response -> {});
    }

    // ---- ModifySubject (single, existing) via alias ----

    @Test
    public void modifySubjectViaAliasModifiesExistingSubjectInAllTargets() {
        // "existing-user" is already in the entriesAdditions targets (set up in setUp())
        final Subject existingSubject = Subject.newInstance(
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "existing-user"), SubjectType.GENERATED);
        final ModifySubject command = ModifySubject.of(POLICY_ID, ALIAS_LABEL, existingSubject, DITTO_HEADERS);

        // Subject already exists → expect SubjectAliasSubjectModified (not Created)
        assertModificationResult(modifySubjectStrategy, policyWithAlias, command,
                SubjectAliasSubjectModified.class,
                event -> {
                    assertThat(event.getAliasLabel()).isEqualTo(ALIAS_LABEL);
                    assertThat(event.getSubject()).isEqualTo(existingSubject);
                    assertThat(event.getTargets()).hasSize(2);
                },
                ModifySubjectResponse.class,
                response -> {});
    }

    // ---- RetrieveSubject (single) via alias ----

    @Test
    public void retrieveSubjectViaAliasReturnsSubjectFromFirstTarget() {
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "existing-user");
        final var command = org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject.of(
                POLICY_ID, ALIAS_LABEL, subjectId, DITTO_HEADERS);

        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final var retrieveSubjectStrategy = new RetrieveSubjectStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
        final Result<?> result = applyStrategy(retrieveSubjectStrategy, context, policyWithAlias, command);

        final Dummy<?> mock = Dummy.mock();
        result.accept(cast(mock), null);

        final ArgumentCaptor<WithDittoHeaders> responseCaptor = ArgumentCaptor.forClass(WithDittoHeaders.class);
        verify(mock).onQuery(any(), responseCaptor.capture());

        assertThat(responseCaptor.getValue()).isInstanceOf(
                org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectResponse.class);
    }

    @Test
    public void retrieveSubjectViaAliasReturnsNotFoundForMissingSubject() {
        final SubjectId unknownSubjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "nonexistent-user");
        final var command = org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject.of(
                POLICY_ID, ALIAS_LABEL, unknownSubjectId, DITTO_HEADERS);

        final var retrieveSubjectStrategy = new RetrieveSubjectStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));

        assertErrorResult(retrieveSubjectStrategy, policyWithAlias, command,
                org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectNotAccessibleException
                        .newBuilder(POLICY_ID, ALIAS_LABEL, unknownSubjectId)
                        .dittoHeaders(DITTO_HEADERS)
                        .build());
    }

    // ---- DeleteSubject (not found) via alias ----

    @Test
    public void deleteNonExistentSubjectViaAliasStillSucceeds() {
        // Delete of a non-existent subject through alias still fans out (the remove is a no-op per target)
        final SubjectId unknownSubjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "nonexistent-user");
        final DeleteSubject command = DeleteSubject.of(POLICY_ID, ALIAS_LABEL, unknownSubjectId, DITTO_HEADERS);

        assertModificationResult(deleteSubjectStrategy, policyWithAlias, command,
                SubjectAliasSubjectDeleted.class,
                event -> {
                    assertThat(event.getAliasLabel()).isEqualTo(ALIAS_LABEL);
                    assertThat(event.getSubjectId()).isEqualTo(unknownSubjectId);
                },
                response -> {});
    }

    // ---- DeleteSubjectAliases (bulk) ----

    @Test
    public void deleteAllSubjectAliases() {
        final var deleteSubjectAliasesStrategy = new DeleteSubjectAliasesStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
        final var command = org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectAliases.of(
                POLICY_ID, DITTO_HEADERS);

        assertModificationResult(deleteSubjectAliasesStrategy, policyWithAlias, command,
                org.eclipse.ditto.policies.model.signals.events.SubjectAliasesDeleted.class,
                event -> {},
                response -> {});
    }

    // ---- DeletePolicyImports rejects when alias references import ----

    @Test
    public void deletePolicyImportsRejectedWhenAliasReferencesImport() {
        final var deletePolicyImportsStrategy = new DeletePolicyImportsStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
        final var command = org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImports.of(
                POLICY_ID, DITTO_HEADERS);

        // policyWithAlias has alias "operator" referencing TEMPLATE_POLICY_ID, so delete should be rejected
        assertErrorResult(deletePolicyImportsStrategy, policyWithAlias, command,
                org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportsNotModifiableException
                        .newBuilder(POLICY_ID)
                        .message("The imports of the Policy with ID '" + POLICY_ID +
                                "' cannot be deleted because they are referenced by subject alias '" +
                                ALIAS_LABEL + "'.")
                        .description("Remove all subject aliases first before deleting the imports.")
                        .dittoHeaders(DITTO_HEADERS)
                        .build());
    }

    // ---- Helpers ----

    private static DittoRuntimeException policyEntryNotFound(final Label label) {
        return org.eclipse.ditto.policies.model.signals.commands.exceptions
                .PolicyEntryNotAccessibleException.newBuilder(POLICY_ID, label)
                .dittoHeaders(DITTO_HEADERS)
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    private static <T extends org.eclipse.ditto.base.model.signals.events.Event<?>> Dummy<T> cast(
            final Dummy<?> dummy) {
        return (Dummy) dummy;
    }

    /** Overloaded assertion for delete which returns void response. */
    private <C extends org.eclipse.ditto.base.model.signals.commands.Command<?>,
            T extends org.eclipse.ditto.policies.model.signals.events.PolicyEvent<?>> void assertModificationResult(
            final CommandStrategy<C, Policy, PolicyId, ?> underTest,
            final Policy policy,
            final C command,
            final Class<T> expectedEventClass,
            final java.util.function.Consumer<T> eventSatisfactions,
            final java.util.function.Consumer<?> responseSatisfactions) {

        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Result<?> result = applyStrategy(underTest, context, policy, command);

        final ArgumentCaptor<T> event = ArgumentCaptor.forClass(expectedEventClass);
        final Dummy<T> mock = Dummy.mock();
        result.accept(cast(mock), null);

        verify(mock).onMutation(any(), event.capture(), any(), anyBoolean(), eq(false), eq(null));
        assertThat(event.getValue()).isInstanceOf(expectedEventClass);
        assertThat(event.getValue()).satisfies(eventSatisfactions);
    }

}
