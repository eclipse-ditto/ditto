/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotCreatableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionNotModifiedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.policies.service.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.policies.service.persistence.actors.PolicyEnforcerActor;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link PolicyCommandEnforcement} in context of a {@link PolicyEnforcerActor}.
 */
public final class PolicyCommandEnforcementTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final SubjectId AUTH_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "someId");
    private static final Subject AUTH_SUBJECT = Subject.newInstance(AUTH_SUBJECT_ID);
    private static final Subject AUTH_SUBJECT_2 =
            Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "secondId"));

    private static final String NAMESPACE = "my.namespace";
    private static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, "policyId");
    private static final String CORRELATION_ID = "test-correlation-id";

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(AUTH_SUBJECT_ID)))
            .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), AUTH_SUBJECT_ID)
            .correlationId(CORRELATION_ID)
            .build();

    private static final DittoHeaders DITTO_HEADERS_WITH_CORRELATION_ID = DittoHeaders.newBuilder()
            .correlationId("sudoRetrievePolicy-" + CORRELATION_ID)
            .build();

    private static final SudoRetrievePolicy SUDO_RETRIEVE_POLICY =
            SudoRetrievePolicy.of(POLICY_ID, DITTO_HEADERS_WITH_CORRELATION_ID);

    private static final ResourceKey POLICIES_ROOT_RESOURCE_KEY = PoliciesResourceType
            .policyResource("/");
    private static final ResourceKey POLICIES_ENTRIES_RESOURCE_KEY = PoliciesResourceType
            .policyResource("/entries");

    private static final PolicyEntry POLICY_ENTRY =
            PolicyEntry.newInstance("defaultLabel", Collections.singleton(AUTH_SUBJECT), Collections.singleton(
                    Resource.newInstance(POLICIES_ROOT_RESOURCE_KEY,
                            EffectedPermissions.newInstance(Permission.DEFAULT_POLICY_PERMISSIONS,
                                    Collections.emptySet()))));
    private static final long POLICY_REVISION = 4712;
    private static final Policy POLICY = Policy.newBuilder(POLICY_ID)
            .setRevision(POLICY_REVISION)
            .set(POLICY_ENTRY)
            .build();
    private static final JsonObject POLICY_FULL_JSON = POLICY.toJson(FieldType.all());

    private ActorSystem system;
    private TestProbe pubSubMediatorProbe;

    private TestProbe policyPersistenceActorProbe;
    private ActorRef supervisor;
    private PolicyEnforcerProvider policyEnforcerProvider;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));

        policyEnforcerProvider = Mockito.mock(PolicyEnforcerProvider.class);
        pubSubMediatorProbe = createPubSubMediatorProbe();
        policyPersistenceActorProbe = createPolicyPersistenceActorProbe();
        supervisor = createPolicyPersistenceSupervisor();
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void createPolicyWhenPolicyDoesNotYetExist() {
        new TestKit(system) {{
            final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);

            supervisor.tell(createPolicy, getRef());

            policyPersistenceActorProbe.expectMsg(createPolicy);
            final ActorRef commandSender = policyPersistenceActorProbe.lastSender();

            final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
            commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

            expectMsg(mockResponse);
            //TODO: verify resolving
        }};
    }

    @Test
    public void createPolicyWhenAuthSubjectDoesNotHaveWritePermission() {
        new TestKit(system) {{
            final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeWrite",
                    Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                            POLICIES_ROOT_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                    Collections.singleton(Permission.WRITE)))));
            final Policy policy = POLICY.toBuilder()
                    .set(revokeWriteEntry)
                    .build();
            final CreatePolicy createPolicy = CreatePolicy.of(policy, DITTO_HEADERS);

            supervisor.tell(createPolicy, getRef());

            expectMsgClass(PolicyNotCreatableException.class);
        }};
    }

    @Test
    public void modifyPolicyWhenAuthSubjectHasWritePermissionIsSuccessful() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);
            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(modifyPolicy, getRef());

            policyPersistenceActorProbe.expectMsg(modifyPolicy);
            final ActorRef commandSender = policyPersistenceActorProbe.lastSender();
            final ModifyPolicyResponse mockResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
            commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

            expectMsg(mockResponse);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void modifyPolicyWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);
            mockPolicyEnforcerResponseWithoutWrite();

            supervisor.tell(modifyPolicy, getRef());

            expectMsgClass(PolicyNotModifiableException.class);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectHasWritePermissionIsSuccessful() {
        new TestKit(system) {{
            final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
            final ModifyPolicyEntries modifyPolicyEntries =
                    ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);
            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(modifyPolicyEntries, getRef());

            policyPersistenceActorProbe.expectMsg(modifyPolicyEntries);
            final ActorRef commandSender = policyPersistenceActorProbe.lastSender();
            final ModifyPolicyEntriesResponse mockResponse =
                    ModifyPolicyEntriesResponse.of(POLICY_ID, DITTO_HEADERS);

            commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

            expectMsg(mockResponse);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        new TestKit(system) {{
            final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
            final ModifyPolicyEntries modifyPolicyEntries =
                    ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);
            mockPolicyEnforcerResponseWithoutWriteOnEntries();

            supervisor.tell(modifyPolicyEntries, getRef());

            expectMsgClass(PolicyNotModifiableException.class);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionReturnsPolicy() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);
            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(retrievePolicy, getRef());

            policyPersistenceActorProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policyPersistenceActorProbe.reply(mockResponse);

            final RetrievePolicyResponse actualResponse = expectMsgClass(mockResponse.getClass());
            assertRetrievePolicyResponse(actualResponse, mockResponse);
            verify(policyEnforcerProvider, times(2)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyWhenConditionHeaderEvaluationFails() {
        new TestKit(system) {{
            final String ifNonMatchHeader = "\"rev:1\"";
            final DittoHeaders dittoHeaders = DITTO_HEADERS.toBuilder()
                    .ifNoneMatch(EntityTagMatchers.fromStrings(ifNonMatchHeader))
                    .build();
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, dittoHeaders);
            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(retrievePolicy, getRef());

            policyPersistenceActorProbe.expectMsg(retrievePolicy);
            final DittoRuntimeException errorReply =
                    PolicyPreconditionNotModifiedException.newBuilder(ifNonMatchHeader, ifNonMatchHeader)
                            .build();
            policyPersistenceActorProbe.reply(errorReply);

            final DittoRuntimeException enforcementReply = expectMsgClass(errorReply.getClass());
            assertThat(enforcementReply).isEqualTo(errorReply);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionExceptEntriesReturnsPartialPolicy() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);
            mockEnforcerProviderWithPolicyWithoutReadOnEntries();

            supervisor.tell(retrievePolicy, getRef());

            policyPersistenceActorProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policyPersistenceActorProbe.reply(mockResponse);

            final RetrievePolicyResponse expectedResponse = RetrievePolicyResponse.of(POLICY_ID,
                    POLICY_FULL_JSON.remove(Policy.JsonFields.ENTRIES.getPointer()), DITTO_HEADERS);
            final RetrievePolicyResponse actualResponse =
                    expectMsgClass(expectedResponse.getClass());
            assertRetrievePolicyResponse(actualResponse, expectedResponse);
            verify(policyEnforcerProvider, times(2)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionOnlyOnEntriesReturnsAlsoAllowlistFields() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);
            mockPolicyEnforcerResponseWithOnlyReadOnEntries();

            supervisor.tell(retrievePolicy, getRef());

            policyPersistenceActorProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policyPersistenceActorProbe.reply(mockResponse);

            final Collection<JsonPointer> allowlist = Collections.singletonList(Policy.JsonFields.ID.getPointer());
            final Collection<JsonPointer> expectedFields = new ArrayList<>();
            expectedFields.add(Policy.JsonFields.ENTRIES.getPointer());
            expectedFields.addAll(allowlist);

            final JsonObject expectedJson = JsonObject.newBuilder()
                    .setAll(POLICY_FULL_JSON, jsonField -> expectedFields.contains(jsonField.getKey().asPointer()))
                    .build();
            final RetrievePolicyResponse expectedResponse =
                    RetrievePolicyResponse.of(POLICY_ID, expectedJson, DITTO_HEADERS);
            final RetrievePolicyResponse actualResponse = expectMsgClass(expectedResponse.getClass());

            assertRetrievePolicyResponse(actualResponse, expectedResponse);
            verify(policyEnforcerProvider, times(2)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectDoesNotHaveReadPermissionFails() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);
            mockPolicyEnforcerResponseWithoutRead();

            supervisor.tell(retrievePolicy, getRef());

            expectMsgClass(PolicyNotAccessibleException.class);
            verify(policyEnforcerProvider).getPolicyEnforcer(POLICY_ID);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyWhenPolicyDoesNotExistFails() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);
            when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                    .thenReturn(CompletableFuture.completedStage(Optional.empty()));

            supervisor.tell(retrievePolicy, getRef());

            expectMsgClass(PolicyNotAccessibleException.class);
            verify(policyEnforcerProvider).getPolicyEnforcer(POLICY_ID);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectHasReadPermissionReturnsEntries() {
        new TestKit(system) {{
            final RetrievePolicyEntries retrievePolicyEntries =
                    RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);

            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(retrievePolicyEntries, getRef());

            policyPersistenceActorProbe.expectMsg(retrievePolicyEntries);
            final RetrievePolicyEntriesResponse mockResponse =
                    RetrievePolicyEntriesResponse.of(POLICY_ID, POLICY.getEntriesSet(), DITTO_HEADERS);
            policyPersistenceActorProbe.reply(mockResponse);

            expectMsg(mockResponse);
            verify(policyEnforcerProvider, times(2)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectDoesNotHaveReadPermissionFails() {
        new TestKit(system) {{
            final RetrievePolicyEntries retrievePolicyEntries =
                    RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);
            mockEnforcerProviderWithPolicyWithoutReadOnEntries();

            supervisor.tell(retrievePolicyEntries, getRef());

            expectMsgClass(PolicyNotAccessibleException.class);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void createPolicyWhenAuthSubjectHasOnlyReadPermission() {
        testCreatePolicy(Collections.singleton(Permission.READ), false, true);
    }

    @Test
    public void createPolicyWhenAuthSubjectHasOnlyWritePermission() {
        testCreatePolicy(Collections.singleton(Permission.WRITE), false, false);
    }

    @Test
    public void createPolicyWhenAuthSubjectHasOnlyReadPermissionWithAllowPolicyLockout() {
        testCreatePolicy(Collections.singleton(Permission.READ), true, false);
    }

    @Test
    public void createPolicyWhenAuthSubjectHasNoPermission() {
        testCreatePolicy(Collections.emptyList(), false, true);
    }

    @Test
    public void createPolicyWhenAuthSubjectHasNoPermissionWithoutPolicyLockoutPrevention() {
        testCreatePolicy(Collections.emptyList(), true, false);
    }

    @Test
    public void activateTopLevelTokenIntegrationWithoutPermission() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final Instant expiry = Instant.now();
            final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                    ActivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId), expiry,
                            DITTO_HEADERS),
                    List.of());
            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(command, getRef());

            expectMsgClass(PolicyActionFailedException.class);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void deactivateTopLevelTokenIntegrationWithoutPermission() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId),
                            DITTO_HEADERS),
                    List.of());
            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(command, getRef());

            expectMsgClass(PolicyActionFailedException.class);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void activateTokenIntegrationWithoutPermission() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final Instant expiry = Instant.now();
            final ActivateTokenIntegration activateTokenIntegration =
                    ActivateTokenIntegration.of(POLICY_ID, Label.of("forbidden"), Collections.singleton(subjectId),
                            expiry, DITTO_HEADERS);
            mockPolicyEnforcerResponseForActions();

            supervisor.tell(activateTokenIntegration, getRef());

            expectMsgClass(PolicyActionFailedException.class);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void deactivateTokenIntegrationWithoutPermission() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final DeactivateTokenIntegration deactivateTokenIntegration =
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("forbidden"), Collections.singleton(subjectId),
                            DITTO_HEADERS);
            mockDefaultPolicyEnforcerResponse();

            supervisor.tell(deactivateTokenIntegration, getRef());

            expectMsgClass(PolicyActionFailedException.class);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void activateTopLevelTokenIntegration() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final Instant expiry = Instant.now();
            final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                    ActivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId), expiry,
                            DITTO_HEADERS),
                    List.of()
            );
            mockPolicyEnforcerResponseForActions();

            supervisor.tell(command, getRef());

            final TopLevelPolicyActionCommand
                    forwarded = policyPersistenceActorProbe.expectMsgClass(TopLevelPolicyActionCommand.class);
            assertThat(forwarded).isEqualTo(TopLevelPolicyActionCommand.of(
                    ActivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId), expiry,
                            DITTO_HEADERS),
                    List.of(Label.of("allowed"))
            ));
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void deactivateTopLevelTokenIntegration() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final TopLevelPolicyActionCommand command = TopLevelPolicyActionCommand.of(
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId),
                            DITTO_HEADERS),
                    List.of()
            );
            mockPolicyEnforcerResponseForActions();

            supervisor.tell(command, getRef());

            final TopLevelPolicyActionCommand
                    forwarded = policyPersistenceActorProbe.expectMsgClass(TopLevelPolicyActionCommand.class);
            assertThat(forwarded).isEqualTo(TopLevelPolicyActionCommand.of(
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId),
                            DITTO_HEADERS),
                    List.of(Label.of("allowed"))
            ));
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void activateTokenIntegration() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final Instant expiry = Instant.now();
            final ActivateTokenIntegration activateTokenIntegration =
                    ActivateTokenIntegration.of(POLICY_ID, Label.of("allowed"), Collections.singleton(subjectId),
                            expiry, DITTO_HEADERS);
            mockPolicyEnforcerResponseForActions();

            supervisor.tell(activateTokenIntegration, getRef());

            final ActivateTokenIntegration forwarded =
                    policyPersistenceActorProbe.expectMsgClass(ActivateTokenIntegration.class);
            assertThat(forwarded).isEqualTo(activateTokenIntegration);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    @Test
    public void deactivateTokenIntegration() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final DeactivateTokenIntegration deactivateTokenIntegration =
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("allowed"), Collections.singleton(subjectId),
                            DITTO_HEADERS);
            mockPolicyEnforcerResponseForActions();

            supervisor.tell(deactivateTokenIntegration, getRef());

            final DeactivateTokenIntegration
                    forwarded = policyPersistenceActorProbe.expectMsgClass(DeactivateTokenIntegration.class);
            assertThat(forwarded).isEqualTo(deactivateTokenIntegration);
            verify(policyEnforcerProvider, times(1)).getPolicyEnforcer(POLICY_ID);
        }};
    }

    public void testCreatePolicy(final Iterable<String> grants, final boolean allowPolicyLockout,
            final boolean shouldFail) {
        new TestKit(system) {{

            final PolicyEntry entry =
                    PolicyEntry.newInstance("defaultLabel", Collections.singleton(AUTH_SUBJECT), Collections.singleton(
                            Resource.newInstance(POLICIES_ROOT_RESOURCE_KEY,
                                    EffectedPermissions.newInstance(grants, Collections.emptySet()))));

            final Policy policy = Policy.newBuilder(POLICY_ID)
                    .setRevision(POLICY_REVISION)
                    .set(entry)
                    .build();

            final CreatePolicy createPolicy = CreatePolicy.of(policy,
                    DITTO_HEADERS.toBuilder().allowPolicyLockout(allowPolicyLockout).build());

            supervisor.tell(createPolicy, getRef());

            if (shouldFail) {
                expectMsgClass(PolicyNotCreatableException.class);
            } else {
                policyPersistenceActorProbe.expectMsg(createPolicy);
                final ActorRef commandSender = policyPersistenceActorProbe.lastSender();

                final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
                commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

                expectMsg(mockResponse);
            }
        }};
    }

    private static void assertRetrievePolicyResponse(final RetrievePolicyResponse actual,
            final RetrievePolicyResponse expected) {

        assertThat((CharSequence) actual.getEntityId()).isEqualTo(expected.getEntityId());
        DittoJsonAssertions.assertThat(actual.getEntity()).hasJsonString(expected.getEntity().toString());
        assertThat(actual.getDittoHeaders()).isEqualTo(expected.getDittoHeaders());
    }

    private void mockDefaultPolicyEnforcerResponse() {
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(POLICY))));
    }

    private void mockPolicyEnforcerResponseWithoutRead() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeRead",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ROOT_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.READ)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));
    }

    private void mockPolicyEnforcerResponseWithoutWrite() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeWrite",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ROOT_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.WRITE)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));
    }

    private void mockEnforcerProviderWithPolicyWithoutReadOnEntries() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeReadOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.READ)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));
    }

    private void mockPolicyEnforcerResponseWithOnlyReadOnEntries() {
        final PolicyEntry readOnEntries = PolicyEntry.newInstance("readOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY,
                        EffectedPermissions.newInstance(Collections.singleton(Permission.READ),
                                Collections.emptySet()))));
        final Policy policy = Policy.newBuilder(POLICY_ID)
                .setRevision(POLICY_REVISION)
                .set(readOnEntries)
                .build();

        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));
    }

    private void mockPolicyEnforcerResponseWithoutWriteOnEntries() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeWriteOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.WRITE)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));
    }

    private void mockPolicyEnforcerResponseForActions() {
        final PolicyEntry adminEntry = PolicyEntry.newInstance("admin",
                List.of(AUTH_SUBJECT_2),
                Set.of(Resource.newInstance(POLICIES_ROOT_RESOURCE_KEY,
                        EffectedPermissions.newInstance(Permission.DEFAULT_POLICY_PERMISSIONS, Set.of()))));
        final PolicyEntry executeEntry = PolicyEntry.newInstance("execute",
                List.of(AUTH_SUBJECT),
                Set.of(Resource.newInstance(
                                PoliciesResourceType.policyResource("/entries/allowed/actions/activateTokenIntegration"),
                                EffectedPermissions.newInstance(Set.of(Permission.EXECUTE), Set.of())),
                        Resource.newInstance(PoliciesResourceType.policyResource(
                                        "/entries/allowed/actions/deactivateTokenIntegration"),
                                EffectedPermissions.newInstance(Set.of(Permission.EXECUTE), Set.of()))
                ));
        final PolicyEntry allowedEntry = PolicyEntry.newInstance("allowed", List.of(), Set.of());
        final PolicyEntry forbiddenEntry = PolicyEntry.newInstance("forbidden", List.of(), Set.of());
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(POLICY_REVISION)
                .set(adminEntry)
                .set(executeEntry)
                .set(allowedEntry)
                .set(forbiddenEntry)
                .build();

        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));
    }

    private TestProbe createPubSubMediatorProbe() {
        return new TestProbe(system, createUniqueName("pubSubMediatorProbe-"));
    }

    private TestProbe createPolicyPersistenceActorProbe() {
        return new TestProbe(system, createUniqueName("policyPersistenceActorProbe-"));
    }

    private TestActorRef<MockPolicyPersistenceSupervisor> createPolicyPersistenceSupervisor() {
        return new TestActorRef<>(system, Props.create(
                MockPolicyPersistenceSupervisor.class,
                () -> new MockPolicyPersistenceSupervisor(pubSubMediatorProbe.ref(),
                        policyPersistenceActorProbe.ref(), policyEnforcerProvider)), system.guardian(),
                MockPolicyPersistenceSupervisor.ACTOR_NAME);
    }

    private static String createUniqueName(final String prefix) {
        return prefix + UUID.randomUUID();
    }

    private class MockPolicyPersistenceSupervisor
            extends AbstractPersistenceSupervisor<PolicyId, PolicyCommand<?>> {

        static final String ACTOR_NAME = "mockPolicyPersistenceSupervisor";

        private final ActorRef pubSubMediator;
        private final PolicyEnforcerProvider policyEnforcerProvider;

        private MockPolicyPersistenceSupervisor(final ActorRef pubSubMediator, final ActorRef policyPersistenceActor,
                final PolicyEnforcerProvider policyEnforcerProvider) {
            super(policyPersistenceActor, null, null, Mockito.mock(MongoReadJournal.class), Duration.ofSeconds(5));
            this.pubSubMediator = pubSubMediator;
            this.policyEnforcerProvider = policyEnforcerProvider;
        }

        @Override
        protected PolicyId getEntityId() throws Exception {
            return POLICY_ID;
        }

        @Override
        protected Props getPersistenceActorProps(final PolicyId entityId) {
            throw new IllegalStateException("This method should never be invoked for the Mock");
        }

        @Override
        protected Props getPersistenceEnforcerProps(final PolicyId entityId) {
            return PolicyEnforcerActor.props(entityId, new PolicyCommandEnforcement(), policyEnforcerProvider);
        }

        @Override
        protected ExponentialBackOffConfig getExponentialBackOffConfig() {
            final DittoPoliciesConfig policiesConfig = DittoPoliciesConfig.of(
                    DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
            );
            return policiesConfig.getPolicyConfig().getSupervisorConfig().getExponentialBackOffConfig();
        }

        @Override
        protected ShutdownBehaviour getShutdownBehaviour(final PolicyId entityId) {
            return ShutdownBehaviour.fromId(entityId, pubSubMediator, getSelf());
        }

        @Override
        protected DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable final PolicyId entityId) {
            final PolicyId policyId = entityId != null ? entityId : PolicyId.of("UNKNOWN:ID");
            return PolicyUnavailableException.newBuilder(policyId);
        }
    }

}
