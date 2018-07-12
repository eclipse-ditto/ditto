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
package org.eclipse.ditto.services.concierge.enforcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.concierge.cache.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotModifiableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntries;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link PolicyCommandEnforcement} and {@link PolicyEnforcerCacheLoader} in context of an
 * {@link EnforcerActorCreator}.
 */
public class PolicyCommandEnforcementTest {

    /**
     * Configure ask-timeout with a quite high value for easier debugging.
     */
    private static final Duration ASK_TIMEOUT = Duration.ofMinutes(5);

    private static final SubjectId AUTH_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "someId");
    private static final Subject AUTH_SUBJECT = Subject.newInstance(AUTH_SUBJECT_ID);

    private static final String NAMESPACE = "my.namespace";
    private static final String POLICY_ID = NAMESPACE + ":policyId";
    private static final String RESOURCE_TYPE = PolicyCommand.RESOURCE_TYPE;
    private static final EntityId ENTITY_ID = EntityId.of(RESOURCE_TYPE, POLICY_ID);

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationSubjects(AUTH_SUBJECT_ID)
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    private static final SudoRetrievePolicy SUDO_RETRIEVE_POLICY =
            SudoRetrievePolicy.of(POLICY_ID, EMPTY_DITTO_HEADERS);

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
    private Cache<EntityId, Entry<Enforcer>> enforcerCache;
    private TestKit testKit;
    private ActorRef enforcer;

    @Before
    public void init() {
        system = ActorSystem.create();

        policiesShardRegionProbe = createPoliciesShardRegionProbe();

        enforcerCache = createCache(new PolicyEnforcerCacheLoader(ASK_TIMEOUT, policiesShardRegionProbe.ref()));

        enforcer = createEnforcer();

        testKit = new TestKit(system);
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void createPolicyWhenPolicyDoesNotYetExist() {
        final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);

        enforcer.tell(createPolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender()
                .tell(PolicyNotAccessibleException.newBuilder(POLICY_ID).build(), policiesShardRegionProbe.ref());

        expectMsg(policiesShardRegionProbe, createPolicy);
        final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        expectMsg(testKit, mockResponse);
    }

    @Test
    public void createPolicyWhenPolicyAlreadyExistsAndAuthSubjectDoesNotHaveWritePermission() {
        final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);

        enforcer.tell(createPolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createPolicyResponseWithoutWrite(), policiesShardRegionProbe.ref());

        testKit.expectMsgClass(PolicyNotAccessibleException.class);
    }

    @Test
    public void modifyPolicyWhenAuthSubjectHasWritePermissionIsSuccessful() {
        final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

        enforcer.tell(modifyPolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

        expectMsg(policiesShardRegionProbe, modifyPolicy);
        final ModifyPolicyResponse mockResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        expectMsg(testKit, mockResponse);
    }

    @Test
    public void modifyPolicyInvalidatesCache() {
        // GIVEN: authorized modify policy command is forwarded and response is received
        final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);
        enforcer.tell(modifyPolicy, testKit.getRef());
        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());
        expectMsg(policiesShardRegionProbe, modifyPolicy);
        final ModifyPolicyResponse mockResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());
        expectMsg(testKit, mockResponse);

        // WHEN: another policy command is sent
        enforcer.tell(modifyPolicy, testKit.getRef());

        // THEN: cache is reloaded; expect SudoRetrievePolicy and not any other command
        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());
        expectMsg(policiesShardRegionProbe, modifyPolicy);
    }

    @Test
    public void modifyPolicyWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

        enforcer.tell(modifyPolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createPolicyResponseWithoutWrite(), policiesShardRegionProbe.ref());

        testKit.expectMsgClass(PolicyNotModifiableException.class);
    }

    @Test
    public void modifyPolicyWhenPolicyDoesNotExistIsHandledAsCreatePolicy() {
        final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);

        enforcer.tell(modifyPolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(PolicyNotAccessibleException.newBuilder(POLICY_ID).build(),
                policiesShardRegionProbe.ref());

        final CreatePolicy expectedCreatePolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);
        expectMsg(policiesShardRegionProbe, expectedCreatePolicy);
        final CreatePolicyResponse mockResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        expectMsg(testKit, mockResponse);
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectHasWritePermissionIsSuccessful() {
        final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
        final ModifyPolicyEntries modifyPolicyEntries =
                ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);

        enforcer.tell(modifyPolicyEntries, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

        expectMsg(policiesShardRegionProbe, modifyPolicyEntries);
        final ModifyPolicyEntries mockResponse =
                ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        expectMsg(testKit, mockResponse);
    }

    @Test
    public void modifyPolicyEntriesWhenAuthSubjectDoesNotHaveWritePermissionFails() {
        final Set<PolicyEntry> modifiedEntries = Collections.singleton(POLICY_ENTRY);
        final ModifyPolicyEntries modifyPolicyEntries =
                ModifyPolicyEntries.of(POLICY_ID, modifiedEntries, DITTO_HEADERS);

        enforcer.tell(modifyPolicyEntries, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender()
                .tell(createPolicyResponseWithoutWriteOnEntries(), policiesShardRegionProbe.ref());

        testKit.expectMsgClass(PolicyNotModifiableException.class);
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionReturnsPolicy() {
        final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

        enforcer.tell(retrievePolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

        expectMsg(policiesShardRegionProbe, retrievePolicy);
        final RetrievePolicyResponse mockResponse =
                RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        final RetrievePolicyResponse actualResponse = testKit.expectMsgClass(mockResponse.getClass());
        assertRetrievePolicyResponse(actualResponse, mockResponse);
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionExceptEntriesReturnsPartialPolicy() {
        final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

        enforcer.tell(retrievePolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender()
                .tell(createPolicyResponseWithoutReadOnEntries(), policiesShardRegionProbe.ref());

        expectMsg(policiesShardRegionProbe, retrievePolicy);
        final RetrievePolicyResponse mockResponse =
                RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        final RetrievePolicyResponse expectedResponse = RetrievePolicyResponse.of(POLICY_ID,
                POLICY_FULL_JSON.remove(Policy.JsonFields.ENTRIES.getPointer()), DITTO_HEADERS);
        final RetrievePolicyResponse actualResponse =
                testKit.expectMsgClass(expectedResponse.getClass());
        assertRetrievePolicyResponse(actualResponse, expectedResponse);
    }

    @Test
    public void retrievePolicyWhenAuthSubjectHasReadPermissionOnlyOnEntriesReturnsAlsoWhitelistFields() {
        final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

        enforcer.tell(retrievePolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender()
                .tell(createPolicyResponseWithOnlyReadOnEntries(), policiesShardRegionProbe.ref());

        expectMsg(policiesShardRegionProbe, retrievePolicy);
        final RetrievePolicyResponse mockResponse =
                RetrievePolicyResponse.of(POLICY_ID, POLICY_FULL_JSON, DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        final Collection<JsonPointer> whiteList =
                Collections.singletonList(Policy.JsonFields.ID.getPointer());
        final List<JsonPointer> expectedFields = new ArrayList<>();
        expectedFields.add(Policy.JsonFields.ENTRIES.getPointer());
        expectedFields.addAll(whiteList);

        final JsonObject expectedJson = JsonObject.newBuilder()
                .setAll(POLICY_FULL_JSON, jsonField -> expectedFields.contains(jsonField.getKey().asPointer()))
                .build();
        final RetrievePolicyResponse expectedResponse =
                RetrievePolicyResponse.of(POLICY_ID, expectedJson, DITTO_HEADERS);
        final RetrievePolicyResponse actualResponse =
                testKit.expectMsgClass(expectedResponse.getClass());
        assertRetrievePolicyResponse(actualResponse, expectedResponse);
    }

    @Test
    public void retrievePolicyWhenAuthSubjectDoesNotHaveReadPermissionFails() {
        final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

        enforcer.tell(retrievePolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createPolicyResponseWithoutRead(), policiesShardRegionProbe.ref());

        testKit.expectMsgClass(PolicyNotAccessibleException.class);
    }

    @Test
    public void retrievePolicyWhenPolicyDoesNotExistFails() {
        final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);

        enforcer.tell(retrievePolicy, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender()
                .tell(PolicyNotAccessibleException.newBuilder(POLICY_ID).build(), policiesShardRegionProbe.ref());

        testKit.expectMsgClass(PolicyNotAccessibleException.class);
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectHasReadPermissionReturnsEntries() {
        final RetrievePolicyEntries retrievePolicyEntries =
                RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);

        enforcer.tell(retrievePolicyEntries, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender().tell(createDefaultPolicyResponse(), policiesShardRegionProbe.ref());

        expectMsg(policiesShardRegionProbe, retrievePolicyEntries);
        final RetrievePolicyEntriesResponse mockResponse =
                RetrievePolicyEntriesResponse.of(POLICY_ID, POLICY.getEntriesSet(), DITTO_HEADERS);
        policiesShardRegionProbe.lastSender().tell(mockResponse, policiesShardRegionProbe.ref());

        expectMsg(testKit, mockResponse);
    }

    @Test
    public void retrievePolicyEntriesWhenAuthSubjectDoesNotHaveReadPermissionFails() {
        final RetrievePolicyEntries retrievePolicyEntries =
                RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);

        enforcer.tell(retrievePolicyEntries, testKit.getRef());

        expectMsg(policiesShardRegionProbe, SUDO_RETRIEVE_POLICY);
        policiesShardRegionProbe.lastSender()
                .tell(createPolicyResponseWithoutReadOnEntries(), policiesShardRegionProbe.ref());

        testKit.expectMsgClass(PolicyNotAccessibleException.class);
    }

    private static void assertRetrievePolicyResponse(final RetrievePolicyResponse actual,
            final RetrievePolicyResponse expected) {

        assertThat(actual.getId()).isEqualTo(expected.getId());
        DittoJsonAssertions.assertThat(actual.getEntity()).hasJsonString(expected.getEntity().toString());
        assertThat(actual.getDittoHeaders()).isEqualTo(expected.getDittoHeaders());
    }

    private static <T> void expectMsg(final TestKit testKit, final T expected) {
        @SuppressWarnings("unchecked") final T actual = (T) testKit.expectMsgClass(expected.getClass());
        assertThat(actual).isEqualTo(expected);
    }

    private static <T> void expectMsg(final akka.testkit.TestKit testKit, final T expected) {
        @SuppressWarnings("unchecked") final T actual = (T) testKit.expectMsgClass(expected.getClass());
        assertThat(actual).isEqualTo(expected);
    }

    private static SudoRetrievePolicyResponse createDefaultPolicyResponse() {
        return SudoRetrievePolicyResponse.of(POLICY_ID, POLICY, EMPTY_DITTO_HEADERS);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutRead() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeRead",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ROOT_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.READ)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, EMPTY_DITTO_HEADERS);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutWrite() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeWrite",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ROOT_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.WRITE)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, EMPTY_DITTO_HEADERS);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutReadOnEntries() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeReadOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.READ)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, EMPTY_DITTO_HEADERS);
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

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, EMPTY_DITTO_HEADERS);
    }

    private static SudoRetrievePolicyResponse createPolicyResponseWithoutWriteOnEntries() {
        final PolicyEntry revokeWriteEntry = PolicyEntry.newInstance("revokeWriteOnEntries",
                Collections.singleton(AUTH_SUBJECT), Collections.singleton(Resource.newInstance(
                        POLICIES_ENTRIES_RESOURCE_KEY, EffectedPermissions.newInstance(Collections.emptySet(),
                                Collections.singleton(Permission.WRITE)))));
        final Policy policy = POLICY.toBuilder()
                .set(revokeWriteEntry)
                .build();

        return SudoRetrievePolicyResponse.of(POLICY_ID, policy, EMPTY_DITTO_HEADERS);
    }

    private TestProbe createPoliciesShardRegionProbe() {
        return new TestProbe(system, createUniqueName("policiesShardRegionProbe-"));
    }

    private ActorRef createEnforcer() {
        final ActorRef pubSubMediator =
                new TestProbe(system, createUniqueName("pubSubMediator-")).ref();

        final PolicyCommandEnforcement.Provider enforcementProvider =
                new PolicyCommandEnforcement.Provider(policiesShardRegionProbe.ref(), enforcerCache);
        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(enforcementProvider);

        return system.actorOf(EnforcerActorCreator.props(pubSubMediator, enforcementProviders, Duration.ofSeconds(10)),
                ENTITY_ID.toString());
    }

    private static String createUniqueName(final String prefix) {
        return prefix + UUID.randomUUID().toString();
    }

    private static <K, V> CaffeineCache<K, V> createCache(
            final AsyncCacheLoader<K, V> loader) {
        return CaffeineCache.of(Caffeine.newBuilder(), loader);
    }

}
