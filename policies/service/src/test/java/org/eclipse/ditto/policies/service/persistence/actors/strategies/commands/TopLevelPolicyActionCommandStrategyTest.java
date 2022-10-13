/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.LABEL;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIdInvalidException;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommandResponse;
import org.eclipse.ditto.policies.model.signals.events.SubjectsDeletedPartially;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModifiedPartially;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.TopLevelPolicyActionCommandStrategy}.
 */
public final class TopLevelPolicyActionCommandStrategyTest extends AbstractPolicyCommandStrategyTest {

    private static final Label DUMMY_LABEL = Label.of("-");

    private TopLevelPolicyActionCommandStrategy underTest;

    @Before
    public void setUp() {
        final PolicyConfig policyConfig = DefaultPolicyConfig.of(ConfigFactory.load("policy-test"));
        final ActorSystem system = ActorSystem.create("test");
        underTest = new TopLevelPolicyActionCommandStrategy(policyConfig, system);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(TopLevelPolicyActionCommandStrategy.class, areImmutable(),
                AllowedReason.assumingFields("policyActionCommandStrategyMap")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void activateTokenIntegration() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(LABEL)
        );
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectsModifiedPartially.class,
                event -> {
                    assertThat(event.getModifiedSubjects()).containsOnlyKeys(LABEL);
                    assertThat(event.getModifiedSubjects().get(LABEL).stream().findFirst().orElseThrow().getId())
                            .isEqualTo(expectedSubjectId);
                    assertThat(event.getModifiedSubjects().get(LABEL).stream().findFirst().orElseThrow().getExpiry())
                            .isNotEmpty();
                },
                TopLevelPolicyActionCommandResponse.class,
                response -> assertThat(response)
                        .isEqualTo(TopLevelPolicyActionCommandResponse.of(context.getState(), dittoHeaders)));
    }

    @Test
    public void activateTokenIntegrationWhenAtLeastOncePolicyEntryMatches() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(LABEL)
        );
        final Label label = Label.of("non-matching-entry");
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .forLabel(label)
                .setSubject(Subject.newInstance(SubjectId.newInstance("foo:bar")))
                .setGrantedPermissions(ResourceKey.newInstance("thing:/"), Permission.READ)
                .build();
        assertModificationResult(underTest, policy, command,
                SubjectsModifiedPartially.class,
                event -> {
                    assertThat(event.getModifiedSubjects()).containsOnlyKeys(LABEL);
                    assertThat(event.getModifiedSubjects().get(LABEL).stream().findFirst().orElseThrow().getId())
                            .isEqualTo(expectedSubjectId);
                    assertThat(event.getModifiedSubjects().get(LABEL).stream().findFirst().orElseThrow().getExpiry())
                            .isNotEmpty();
                },
                TopLevelPolicyActionCommandResponse.class,
                response -> assertThat(response)
                        .isEqualTo(TopLevelPolicyActionCommandResponse.of(context.getState(), dittoHeaders)));
    }

    @Test
    public void activateInvalidSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(LABEL)
        );
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void activateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{ policy:id }}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(LABEL)
        );
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{ policy:id }}").build());
    }

    @Test
    public void activateTokenIntegrationWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(LABEL)
        );
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }

    @Test
    public void activate2SubjectsIn2Entries() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final SubjectId subjectId2 = SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me-2");
        final Set<SubjectId> subjectIds = Set.of(subjectId, subjectId2);
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final Label label2 = Label.of("label2");
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, subjectIds, expiry, dittoHeaders),
                List.of(LABEL, label2)
        );

        final PolicyEntry entryToCopy = TestConstants.Policy.POLICY.getEntryFor(LABEL).orElseThrow();
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .forLabel(label2)
                .setSubjects(entryToCopy.getSubjects())
                .setResources(entryToCopy.getResources())
                .build();

        assertModificationResult(underTest, policy, command,
                SubjectsModifiedPartially.class,
                event -> {
                    assertThat(event.getModifiedSubjects()).containsOnlyKeys(LABEL, label2);
                    final Map<Label, Collection<Subject>> modifiedSubjects = event.getModifiedSubjects();
                    final Set<SubjectId> labelSubjectIds =
                            modifiedSubjects.get(LABEL).stream().map(Subject::getId).collect(Collectors.toSet());
                    final Set<SubjectId> label2SubjectIds =
                            modifiedSubjects.get(label2).stream().map(Subject::getId).collect(Collectors.toSet());
                    assertThat(labelSubjectIds)
                            .describedAs("Subject IDs in entry <{}>", LABEL)
                            .isEqualTo(subjectIds);
                    assertThat(label2SubjectIds)
                            .describedAs("Subject IDs in entry <{}>", label2)
                            .isEqualTo(subjectIds);
                },
                TopLevelPolicyActionCommandResponse.class,
                response -> assertThat(response)
                        .isEqualTo(TopLevelPolicyActionCommandResponse.of(context.getState(), dittoHeaders)));
    }

    @Test
    public void rejectEmptyLabels() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of()
        );
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                command.getNotApplicableException(dittoHeaders));
    }

    @Test
    public void rejectNonexistentLabel() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Label nonexistentLabel = Label.of("nonexistent-label");
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(nonexistentLabel)
        );
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                command.getNotApplicableException(dittoHeaders));
    }

    @Test
    public void rejectEntryWithoutThingReadPermission() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Label label = Label.of("empty-entry");
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(label)
        );
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .forLabel(label)
                .setSubject(TestConstants.Policy.SUPPORT_SUBJECT)
                .setGrantedPermissions(ResourceKey.newInstance("policy:/"), Permission.READ)
                .build();
        assertErrorResult(underTest, policy, command,
                command.getNotApplicableException(dittoHeaders));
    }

    @Test
    public void rejectEntryWithoutAuthenticatedSubjectIdPresentPermission() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Label label = Label.of("empty-entry");
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                ActivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId), expiry,
                        dittoHeaders),
                List.of(label)
        );
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .forLabel(label)
                .setSubject(TestConstants.Policy.SUPPORT_SUBJECT)
                .setGrantedPermissions(ResourceKey.newInstance("thing:/"), Permission.READ)
                .setGrantedPermissions(ResourceKey.newInstance("policy:/"), Permission.READ)
                .build();
        assertErrorResult(underTest, policy, command, command.getNotApplicableException(dittoHeaders));
    }

    @Test
    public void deactivateTokenIntegration() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                DeactivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId),
                        dittoHeaders),
                List.of(LABEL)
        );
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .setSubjectFor(LABEL, PoliciesModelFactory.newSubject(expectedSubjectId,
                        PoliciesModelFactory.newSubjectType(MessageFormat.format("via action <{0}>",
                                ActivateTokenIntegration.NAME)),
                        SubjectExpiry.newInstance(Instant.now().plus(Duration.ofDays(1L)))))
                .build();
        assertModificationResult(underTest, policy, command,
                SubjectsDeletedPartially.class,
                event -> {
                    assertThat(event.getDeletedSubjectIds()).containsOnlyKeys(LABEL);
                    assertThat(event.getDeletedSubjectIds().get(LABEL))
                            .containsExactly(expectedSubjectId);
                },
                TopLevelPolicyActionCommandResponse.class,
                response -> assertThat(response)
                        .isEqualTo(TopLevelPolicyActionCommandResponse.of(context.getState(), dittoHeaders)));
    }

    @Test
    public void deactivateNonexistentSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                DeactivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId),
                        dittoHeaders),
                List.of(LABEL)
        );
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectsDeletedPartially.class,
                // nonexistent subjects are present in the event.
                event -> assertThat(event.getDeletedSubjectIds()).containsExactly(
                        Map.entry(LABEL,
                                Collections.singleton(SubjectId.newInstance("integration:" + LABEL + ":this-is-me")))),
                TopLevelPolicyActionCommandResponse.class,
                response -> assertThat(response)
                        .isEqualTo(TopLevelPolicyActionCommandResponse.of(context.getState(), dittoHeaders)));
    }

    @Test
    public void deactivateInvalidSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                DeactivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId),
                        dittoHeaders),
                List.of(LABEL)
        );
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void deactivateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{ policy:id }}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                DeactivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId),
                        dittoHeaders),
                List.of(LABEL)
        );
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{ policy:id }}").build());
    }

    @Test
    public void deactivateTokenIntegrationWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                DeactivateTokenIntegration.of(context.getState(), DUMMY_LABEL, Collections.singleton(subjectId),
                        dittoHeaders),
                List.of(LABEL)
        );
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }

}
