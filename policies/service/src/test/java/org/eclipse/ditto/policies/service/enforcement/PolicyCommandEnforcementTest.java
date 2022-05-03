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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.api.commands.sudo.PolicySudoCommand;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.enforcement.DefaultCreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.config.DefaultEntityCreationConfig;
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
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionNotModifiedException;
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
import org.eclipse.ditto.policies.service.persistence.actors.PolicyEnforcerActor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link PolicyCommandEnforcement} in context of a {@link PolicyEnforcerActor}.
 */
public final class PolicyCommandEnforcementTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyCommandEnforcementTest.class);

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
            .correlationId(CORRELATION_ID)
            .putHeader(DittoHeaderDefinition.POLICY_ENFORCER_INVALIDATED_PREEMPTIVELY.getKey(), "true")
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
    private ActorRef enforcerParent;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));

        pubSubMediatorProbe = createPubSubMediatorProbe();
        policyPersistenceActorProbe = createPolicyPersistenceActorProbe();
        enforcerParent = createEnforcerParent();
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

            enforcerParent.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(PolicyNotAccessibleException.newBuilder(POLICY_ID).build());

            policyPersistenceActorProbe.expectMsg(createPolicy);
            final ActorRef commandSender = policyPersistenceActorProbe.lastSender();

            final SudoRetrievePolicy sudoRetrievePolicy2 =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy2.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
            commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void createPolicyWhenPolicyAlreadyExistsAndAuthSubjectDoesNotHaveWritePermission() {
        new TestKit(system) {{
            final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);

            enforcerParent.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithoutWrite());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void modifyPolicyWhenAuthSubjectHasWritePermissionIsSuccessful() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

            enforcerParent.tell(modifyPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            policyPersistenceActorProbe.expectMsg(modifyPolicy);
            final ActorRef commandSender = policyPersistenceActorProbe.lastSender();
            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            final ModifyPolicyResponse mockResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
            commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void modifyPolicyInvalidatesCache() {
        new TestKit(system) {{
            // GIVEN: authorized modify policy command is forwarded and response is received
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);
            enforcerParent.tell(modifyPolicy, getRef());
            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());
            policyPersistenceActorProbe.expectMsg(modifyPolicy);
            final ActorRef modifyCommandSender1 = policyPersistenceActorProbe.lastSender();

            // THEN: cache is reloaded; expect SudoRetrievePolicy
            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            final ModifyPolicyResponse mockResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
            modifyCommandSender1.tell(mockResponse, policyPersistenceActorProbe.ref());
            expectMsg(mockResponse);

            // WHEN: policy gets updated and emits that change via PolicyTag
            enforcerParent.tell(PolicyTag.of(POLICY_ID, 44), getRef());

            // THEN: cache is reloaded; expect SudoRetrievePolicy and not any other command
            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            // WHEN: authorized modify policy command is forwarded and response is received
            enforcerParent.tell(modifyPolicy, getRef());
            policyPersistenceActorProbe.expectMsg(modifyPolicy);
            final ActorRef modifyCommandSender2 = policyPersistenceActorProbe.lastSender();

            // THEN: cache is reloaded; expect SudoRetrievePolicy
            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            final ModifyPolicyResponse mockResponse2 = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
            modifyCommandSender2.tell(mockResponse2, policyPersistenceActorProbe.ref());
            expectMsg(mockResponse2);
        }};
    }

    @Test
    public void modifyPolicyWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

            enforcerParent.tell(modifyPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithoutWrite());

            expectMsgClass(PolicyNotModifiableException.class);
        }};
    }

    @Test
    public void modifyPolicyWhenPolicyDoesNotExistIsHandledAsCreatePolicy() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

            enforcerParent.tell(modifyPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(PolicyNotAccessibleException.newBuilder(POLICY_ID).build());

            final CreatePolicy expectedCreatePolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);
            policyPersistenceActorProbe.expectMsg(expectedCreatePolicy);
            final ActorRef commandSender = policyPersistenceActorProbe.lastSender();

            final SudoRetrievePolicy sudoRetrievePolicy2 =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy2.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
            commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectHasWritePermissionIsSuccessful() {
        new TestKit(system) {{
            final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
            final ModifyPolicyEntries modifyPolicyEntries =
                    ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);

            enforcerParent.tell(modifyPolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            policyPersistenceActorProbe.expectMsg(modifyPolicyEntries);
            final ActorRef commandSender = policyPersistenceActorProbe.lastSender();
            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            final ModifyPolicyEntriesResponse mockResponse =
                    ModifyPolicyEntriesResponse.of(POLICY_ID, DITTO_HEADERS);
            commandSender.tell(mockResponse, policyPersistenceActorProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        new TestKit(system) {{
            final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
            final ModifyPolicyEntries modifyPolicyEntries =
                    ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);

            enforcerParent.tell(modifyPolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithoutWriteOnEntries());

            expectMsgClass(PolicyNotModifiableException.class);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionReturnsPolicy() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcerParent.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            policyPersistenceActorProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policyPersistenceActorProbe.reply(mockResponse);

            final RetrievePolicyResponse actualResponse = expectMsgClass(mockResponse.getClass());
            assertRetrievePolicyResponse(actualResponse, mockResponse);
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

            enforcerParent.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            policyPersistenceActorProbe.expectMsg(retrievePolicy);
            final DittoRuntimeException errorReply =
                    PolicyPreconditionNotModifiedException.newBuilder(ifNonMatchHeader, ifNonMatchHeader)
                            .build();
            policyPersistenceActorProbe.reply(errorReply);

            final DittoRuntimeException enforcementReply = expectMsgClass(errorReply.getClass());
            assertThat(enforcementReply).isEqualTo(errorReply);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionExceptEntriesReturnsPartialPolicy() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcerParent.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy = policyPersistenceActorProbe.expectMsgClass(
                    FiniteDuration.apply(dilated(Duration.ofSeconds(5)).toMillis(), TimeUnit.MILLISECONDS),
                    SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithoutReadOnEntries());

            policyPersistenceActorProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policyPersistenceActorProbe.reply(mockResponse);

            final RetrievePolicyResponse expectedResponse = RetrievePolicyResponse.of(POLICY_ID,
                    POLICY_FULL_JSON.remove(Policy.JsonFields.ENTRIES.getPointer()), DITTO_HEADERS);
            final RetrievePolicyResponse actualResponse =
                    expectMsgClass(expectedResponse.getClass());
            assertRetrievePolicyResponse(actualResponse, expectedResponse);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionOnlyOnEntriesReturnsAlsoAllowlistFields() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcerParent.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithOnlyReadOnEntries());

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
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectDoesNotHaveReadPermissionFails() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcerParent.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithoutRead());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void retrievePolicyWhenPolicyDoesNotExistFails() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcerParent.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(PolicyNotAccessibleException.newBuilder(POLICY_ID).build());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectHasReadPermissionReturnsEntries() {
        new TestKit(system) {{
            final RetrievePolicyEntries retrievePolicyEntries =
                    RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);

            enforcerParent.tell(retrievePolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

            policyPersistenceActorProbe.expectMsg(retrievePolicyEntries);
            final RetrievePolicyEntriesResponse mockResponse =
                    RetrievePolicyEntriesResponse.of(POLICY_ID, POLICY.getEntriesSet(), DITTO_HEADERS);
            policyPersistenceActorProbe.reply(mockResponse);

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectDoesNotHaveReadPermissionFails() {
        new TestKit(system) {{
            final RetrievePolicyEntries retrievePolicyEntries =
                    RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);

            enforcerParent.tell(retrievePolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithoutReadOnEntries());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void createPolicyWhenAuthSubjectHasWritePermission() {
        new TestKit(system) {{
            final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);

            enforcerParent.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(createPolicyResponseWithoutWrite());

            expectMsgClass(PolicyNotAccessibleException.class);
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

            enforcerParent.tell(command, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());
            expectMsgClass(PolicyActionFailedException.class);
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

            enforcerParent.tell(command, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());
            expectMsgClass(PolicyActionFailedException.class);
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

            enforcerParent.tell(activateTokenIntegration, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createPolicyResponseForActions());
            expectMsgClass(PolicyActionFailedException.class);
        }};
    }

    @Test
    public void deactivateTokenIntegrationWithoutPermission() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final DeactivateTokenIntegration deactivateTokenIntegration =
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("forbidden"), Collections.singleton(subjectId),
                            DITTO_HEADERS);

            enforcerParent.tell(deactivateTokenIntegration, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createDefaultPolicyResponse());
            expectMsgClass(PolicyActionFailedException.class);
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

            enforcerParent.tell(command, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createPolicyResponseForActions());

            final TopLevelPolicyActionCommand
                    forwarded = policyPersistenceActorProbe.expectMsgClass(TopLevelPolicyActionCommand.class);
            assertThat(forwarded).isEqualTo(TopLevelPolicyActionCommand.of(
                    ActivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId), expiry,
                            DITTO_HEADERS),
                    List.of(Label.of("allowed"))
            ));
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

            enforcerParent.tell(command, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createPolicyResponseForActions());

            final TopLevelPolicyActionCommand
                    forwarded = policyPersistenceActorProbe.expectMsgClass(TopLevelPolicyActionCommand.class);
            assertThat(forwarded).isEqualTo(TopLevelPolicyActionCommand.of(
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("-"), Collections.singleton(subjectId),
                            DITTO_HEADERS),
                    List.of(Label.of("allowed"))
            ));
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

            enforcerParent.tell(activateTokenIntegration, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createPolicyResponseForActions());

            final ActivateTokenIntegration forwarded =
                    policyPersistenceActorProbe.expectMsgClass(ActivateTokenIntegration.class);
            assertThat(forwarded).isEqualTo(activateTokenIntegration);
        }};
    }

    @Test
    public void deactivateTokenIntegration() {
        new TestKit(system) {{
            final SubjectId subjectId = SubjectId.newInstance("issuer:{{policy-entry:label}}:subject");
            final DeactivateTokenIntegration deactivateTokenIntegration =
                    DeactivateTokenIntegration.of(POLICY_ID, Label.of("allowed"), Collections.singleton(subjectId),
                            DITTO_HEADERS);

            enforcerParent.tell(deactivateTokenIntegration, getRef());

            policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            policyPersistenceActorProbe.reply(createPolicyResponseForActions());

            final DeactivateTokenIntegration
                    forwarded = policyPersistenceActorProbe.expectMsgClass(DeactivateTokenIntegration.class);
            assertThat(forwarded).isEqualTo(deactivateTokenIntegration);
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

            enforcerParent.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policyPersistenceActorProbe.reply(PolicyNotAccessibleException.newBuilder(POLICY_ID).build());

            if (shouldFail) {
                expectMsgClass(PolicyNotAccessibleException.class);
            } else {
                policyPersistenceActorProbe.expectMsg(createPolicy);
                final ActorRef commandSender = policyPersistenceActorProbe.lastSender();

                final SudoRetrievePolicy sudoRetrievePolicy2 =
                        policyPersistenceActorProbe.expectMsgClass(SudoRetrievePolicy.class);
                assertThat((CharSequence) sudoRetrievePolicy2.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
                policyPersistenceActorProbe.reply(createDefaultPolicyResponse());

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

    private static SudoRetrievePolicyResponse createDefaultPolicyResponse() {
        return SudoRetrievePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS_WITH_CORRELATION_ID);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutRead() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeRead",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ROOT_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.READ)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, DITTO_HEADERS_WITH_CORRELATION_ID);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutWrite() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeWrite",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ROOT_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.WRITE)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, DITTO_HEADERS_WITH_CORRELATION_ID);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutReadOnEntries() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeReadOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.READ)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, DITTO_HEADERS_WITH_CORRELATION_ID);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithOnlyReadOnEntries() {
        final PolicyEntry readOnEntries = PolicyEntry.newInstance("readOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY,
                        EffectedPermissions.newInstance(Collections.singleton(Permission.READ),
                                Collections.emptySet()))));
        final Policy policy = Policy.newBuilder(POLICY_ID)
                .setRevision(POLICY_REVISION)
                .set(readOnEntries)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, DITTO_HEADERS_WITH_CORRELATION_ID);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutWriteOnEntries() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeWriteOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.WRITE)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, DITTO_HEADERS_WITH_CORRELATION_ID);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseForActions() {
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

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, DITTO_HEADERS_WITH_CORRELATION_ID);
    }

    private TestProbe createPubSubMediatorProbe() {
        return new TestProbe(system, createUniqueName("pubSubMediatorProbe-"));
    }

    private TestProbe createPolicyPersistenceActorProbe() {
        return new TestProbe(system, createUniqueName("policyPersistenceActorProbe-"));
    }

    private ActorRef createEnforcerParent() {
        return system.actorOf(Props.create(MockPolicyEnforcerParentActor.class, pubSubMediatorProbe.ref(),
                        policyPersistenceActorProbe.ref()),
                MockPolicyEnforcerParentActor.ACTOR_NAME);
    }

    private static String createUniqueName(final String prefix) {
        return prefix + UUID.randomUUID();
    }

    private static class MockPolicyEnforcerParentActor extends AbstractActor {

        private final DiagnosticLoggingAdapter log = Logging.apply(this);

        static final String ACTOR_NAME = "mockPolicyEnforcerParentActor";
        static final String POLICY_ENFORCER_ACTOR_NAME = "policyEnforcerActor";

        private final ActorRef policyPersistenceActor;
        private final ActorRef policyEnforcerActor;

        private MockPolicyEnforcerParentActor(final ActorRef pubSubMediator, final ActorRef policyPersistenceActor) {
            this.policyPersistenceActor = policyPersistenceActor;
            policyEnforcerActor = getContext().actorOf(PolicyEnforcerActor.props(
                    POLICY_ID,
                    new PolicyCommandEnforcement(
                            DefaultCreationRestrictionEnforcer.of(DefaultEntityCreationConfig.of(ConfigFactory.empty()))
                    ),
                    pubSubMediator,
                    null
            ), POLICY_ENFORCER_ACTOR_NAME);
        }

        @Override
        public AbstractActor.Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(PolicyTag.class, policyTag -> policyEnforcerActor.forward(policyTag, getContext()))
                    .match(PolicySudoCommand.class, sudoCommand ->
                            policyPersistenceActor.forward(sudoCommand, getContext()))
                    .match(PolicyCommand.class, command -> {
                        final ActorRef sender = getSender();
                        Patterns.ask(policyEnforcerActor, command, Duration.ofSeconds(2))
                                .whenComplete((enResponse, enThrowable) -> {
                                    if (enResponse instanceof PolicyCommand<?> enforcedCommand) {
                                        Patterns.ask(policyPersistenceActor, enforcedCommand, Duration.ofSeconds(2))
                                                .whenComplete((paResponse, paThrowable) -> {
                                                    if (paResponse instanceof PolicyCommandResponse<?> commandResponse) {
                                                        policyEnforcerActor.tell(commandResponse, sender);
                                                    } else if (paResponse instanceof DittoRuntimeException dre) {
                                                        sender.tell(dre, policyPersistenceActor);
                                                    } else if (null != paThrowable) {
                                                        sender.tell(paThrowable, policyPersistenceActor);
                                                    } else {
                                                        LOGGER.error("Received non expected response: {}", paResponse);
                                                    }
                                                });
                                    } else if (enResponse instanceof DittoRuntimeException dre) {
                                        sender.tell(dre, policyPersistenceActor);
                                    } else if (null != enThrowable) {
                                        sender.tell(enThrowable, policyPersistenceActor);
                                    }
                                });
                    })
                    .matchAny(msg -> log.warning("Unhandled message: <{}>", msg))
                    .build();
        }
    }


}
