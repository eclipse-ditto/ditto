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
package org.eclipse.ditto.concierge.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CaffeineCache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
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
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link PolicyCommandEnforcement} and {@link org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcerCacheLoader} in context of an
 * {@link EnforcerActor}.
 */
public final class PolicyCommandEnforcementTest {

    private static final SubjectId AUTH_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "someId");
    private static final Subject AUTH_SUBJECT = Subject.newInstance(AUTH_SUBJECT_ID);
    private static final Subject AUTH_SUBJECT_2 =
            Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "secondId"));

    private static final String NAMESPACE = "my.namespace";
    private static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, "policyId");
    private static final String CORRELATION_ID = "test-correlation-id";
    private static final EnforcementCacheKey ENTITY_ID = EnforcementCacheKey.of(POLICY_ID);

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
    private TestProbe policiesShardRegionProbe;
    private Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> enforcerCache;
    private ActorRef enforcer;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));

        policiesShardRegionProbe = createPoliciesShardRegionProbe();

        enforcerCache = createCache(new PolicyEnforcerCacheLoader(
                DefaultAskWithRetryConfig.of(ConfigFactory.empty(), "test"),
                system.getScheduler(), policiesShardRegionProbe.ref()));

        enforcer = createEnforcer();
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

            enforcer.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(PolicyNotAccessibleException.newBuilder(POLICY_ID).build(),
                            policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(createPolicy);
            final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void createPolicyWhenPolicyAlreadyExistsAndAuthSubjectDoesNotHaveWritePermission() {
        new TestKit(system) {{
            final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);

            enforcer.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithoutWrite(), policiesShardRegionProbe.ref());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void modifyPolicyWhenAuthSubjectHasWritePermissionIsSuccessful() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

            enforcer.tell(modifyPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(modifyPolicy);
            final ModifyPolicyResponse mockResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void modifyPolicyInvalidatesCache() {
        new TestKit(system) {{
            // GIVEN: authorized modify policy command is forwarded and response is received
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);
            enforcer.tell(modifyPolicy, getRef());
            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());
            policiesShardRegionProbe.expectMsg(modifyPolicy);
            final ModifyPolicyResponse mockResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());
            expectMsg(mockResponse);

            // WHEN: another policy command is sent
            enforcer.tell(modifyPolicy, getRef());

            // THEN: cache is reloaded; expect SudoRetrievePolicy and not any other command
            final SudoRetrievePolicy sudoRetrievePolicy2 =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy2.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());
            policiesShardRegionProbe.expectMsg(modifyPolicy);
        }};
    }

    @Test
    public void modifyPolicyWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

            enforcer.tell(modifyPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithoutWrite(), policiesShardRegionProbe.ref());

            expectMsgClass(PolicyNotModifiableException.class);
        }};
    }

    @Test
    public void modifyPolicyWhenPolicyDoesNotExistIsHandledAsCreatePolicy() {
        new TestKit(system) {{
            final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

            enforcer.tell(modifyPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(PolicyNotAccessibleException.newBuilder(POLICY_ID).build(),
                    policiesShardRegionProbe.ref());

            final CreatePolicy expectedCreatePolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);
            policiesShardRegionProbe.expectMsg(expectedCreatePolicy);
            final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectHasWritePermissionIsSuccessful() {
        new TestKit(system) {{
            final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
            final ModifyPolicyEntries modifyPolicyEntries =
                    ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);

            enforcer.tell(modifyPolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(modifyPolicyEntries);
            final ModifyPolicyEntries mockResponse =
                    ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        new TestKit(system) {{
            final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
            final ModifyPolicyEntries modifyPolicyEntries =
                    ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);

            enforcer.tell(modifyPolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithoutWriteOnEntries(), policiesShardRegionProbe.ref());

            expectMsgClass(PolicyNotModifiableException.class);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionReturnsPolicy() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcer.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

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

            enforcer.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(retrievePolicy);
            final DittoRuntimeException errorReply =
                    PolicyPreconditionNotModifiedException.newBuilder(ifNonMatchHeader, ifNonMatchHeader)
                            .build();
            policiesShardRegionProbe.lastSender().tell(errorReply, policiesShardRegionProbe.ref());

            final DittoRuntimeException enforcementReply = expectMsgClass(errorReply.getClass());
            assertThat(enforcementReply).isEqualTo(errorReply);
        }};
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionExceptEntriesReturnsPartialPolicy() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcer.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy = policiesShardRegionProbe.expectMsgClass(
                    FiniteDuration.apply(dilated(Duration.ofSeconds(5)).toMillis(), TimeUnit.MILLISECONDS),
                    SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithoutReadOnEntries(), policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

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

            enforcer.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithOnlyReadOnEntries(), policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(retrievePolicy);
            final RetrievePolicyResponse mockResponse =
                    RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

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

            enforcer.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithoutRead(), policiesShardRegionProbe.ref());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void retrievePolicyWhenPolicyDoesNotExistFails() {
        new TestKit(system) {{
            final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

            enforcer.tell(retrievePolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(PolicyNotAccessibleException.newBuilder(POLICY_ID).build(), policiesShardRegionProbe.ref());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectHasReadPermissionReturnsEntries() {
        new TestKit(system) {{
            final RetrievePolicyEntries retrievePolicyEntries =
                    RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);

            enforcer.tell(retrievePolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

            policiesShardRegionProbe.expectMsg(retrievePolicyEntries);
            final RetrievePolicyEntriesResponse mockResponse =
                    RetrievePolicyEntriesResponse.of(POLICY_ID, POLICY.getEntriesSet(), DITTO_HEADERS);
            policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

            expectMsg(mockResponse);
        }};
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectDoesNotHaveReadPermissionFails() {
        new TestKit(system) {{
            final RetrievePolicyEntries retrievePolicyEntries =
                    RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);

            enforcer.tell(retrievePolicyEntries, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithoutReadOnEntries(), policiesShardRegionProbe.ref());

            expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void createPolicyWhenAuthSubjectHasWritePermission() {
        new TestKit(system) {{
            final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);

            enforcer.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(createPolicyResponseWithoutWrite(), policiesShardRegionProbe.ref());

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

            enforcer.tell(command, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createDefaultPolicyResponse());
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

            enforcer.tell(command, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createDefaultPolicyResponse());
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

            enforcer.tell(activateTokenIntegration, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createPolicyResponseForActions());
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

            enforcer.tell(deactivateTokenIntegration, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createDefaultPolicyResponse());
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

            enforcer.tell(command, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createPolicyResponseForActions());

            final TopLevelPolicyActionCommand
                    forwarded = policiesShardRegionProbe.expectMsgClass(TopLevelPolicyActionCommand.class);
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

            enforcer.tell(command, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createPolicyResponseForActions());

            final TopLevelPolicyActionCommand
                    forwarded = policiesShardRegionProbe.expectMsgClass(TopLevelPolicyActionCommand.class);
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

            enforcer.tell(activateTokenIntegration, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createPolicyResponseForActions());

            final ActivateTokenIntegration forwarded =
                    policiesShardRegionProbe.expectMsgClass(ActivateTokenIntegration.class);
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

            enforcer.tell(deactivateTokenIntegration, getRef());

            policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            policiesShardRegionProbe.reply(createPolicyResponseForActions());

            final DeactivateTokenIntegration
                    forwarded = policiesShardRegionProbe.expectMsgClass(DeactivateTokenIntegration.class);
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

            enforcer.tell(createPolicy, getRef());

            final SudoRetrievePolicy sudoRetrievePolicy =
                    policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
            assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(SUDO_RETRIEVE_POLICY.getEntityId());
            policiesShardRegionProbe.lastSender()
                    .tell(PolicyNotAccessibleException.newBuilder(POLICY_ID).build(),
                            policiesShardRegionProbe.ref());

            if (shouldFail) {
                expectMsgClass(PolicyNotAccessibleException.class);
            } else {
                policiesShardRegionProbe.expectMsg(createPolicy);
                final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
                policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

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

    private TestProbe createPoliciesShardRegionProbe() {
        return new TestProbe(system, createUniqueName("policiesShardRegionProbe-"));
    }

    private ActorRef createEnforcer() {
        final ActorRef pubSubMediator = new TestProbe(system, createUniqueName("pubSubMediator-")).ref();
        final ActorRef conciergeForwarder = new TestProbe(system, createUniqueName("conciergeForwarder-")).ref();

        final PolicyCommandEnforcement.Provider enforcementProvider =
                new PolicyCommandEnforcement.Provider(policiesShardRegionProbe.ref(), enforcerCache, null);
        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(enforcementProvider);

        return system.actorOf(EnforcerActor.props(pubSubMediator, enforcementProviders, conciergeForwarder,
                        null, null, null),
                ENTITY_ID.toString());
    }

    private static String createUniqueName(final String prefix) {
        return prefix + UUID.randomUUID();
    }

    private static <K, V> CaffeineCache<K, V> createCache(final AsyncCacheLoader<K, V> loader) {
        return CaffeineCache.of(Caffeine.newBuilder(), loader);
    }

}
