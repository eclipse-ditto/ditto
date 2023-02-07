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
package org.eclipse.ditto.policies.service.persistence.actors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceActor.JOURNAL_TAG_ALWAYS_ALIVE;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUBJECT_TYPE;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.modifyPolicyEntryResponse;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.modifyPolicyResponse;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.modifyResourceResponse;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.modifySubjectResponse;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.retrievePolicyEntryResponse;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.retrievePolicyResponse;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.retrieveResourceResponse;
import static org.eclipse.ditto.policies.service.persistence.testhelper.ETagTestUtils.retrieveSubjectResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.DittoSystemProperties;
import org.eclipse.ditto.base.model.entity.Revision;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceActor;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.PolicyTooLargeException;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectExpiryInvalidException;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResourceResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectsResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;
import org.eclipse.ditto.policies.model.signals.events.SubjectDeleted;
import org.eclipse.ditto.policies.service.common.config.PolicyAnnouncementConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.eclipse.ditto.policies.service.persistence.actors.announcements.PolicyAnnouncementManager;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.PartialFunction;

/**
 * Unit test for the {@link PolicyPersistenceActor}.
 */
public final class PolicyPersistenceActorTest extends PersistenceActorTestBase {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyPersistenceActorTest.class);

    private static final long POLICY_SIZE_LIMIT_BYTES = Long.parseLong(
            System.getProperty(DittoSystemProperties.DITTO_LIMITS_POLICIES_MAX_SIZE_BYTES, "-1"));

    @SuppressWarnings("unchecked")
    private final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub = Mockito.mock(DistributedPub.class);

    @Before
    public void setup() {
        setUpBase();
    }

    @Test
    public void tryToRetrievePolicyWhichWasNotYetCreated() {
        final PolicyId policyId = PolicyId.of("test.ns", "23420815");
        final PolicyCommand<?> retrievePolicyCommand = RetrievePolicy.of(policyId, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policyId);
                policyPersistenceActor.tell(retrievePolicyCommand, getRef());
                expectMsgClass(PolicyNotAccessibleException.class);
            }
        };
    }

    @Test
    public void createPolicy() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);
            }
        };
    }

    @Test
    public void modifyPolicy() {
        final Policy policy = createPolicyWithRandomId();
        final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);

        final Policy modifiedPolicy = policy.setEntry(
                PoliciesModelFactory.newPolicyEntry(Label.of("anotherOne"), POLICY_SUBJECTS, POLICY_RESOURCES_ALL));
        final ModifyPolicy modifyPolicyCommand =
                ModifyPolicy.of(policy.getEntityId().get(), modifiedPolicy, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(this, policy);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicyResponse.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                underTest.tell(modifyPolicyCommand, getRef());
                expectMsgEquals(modifyPolicyResponse(incrementRevision(policy, 2), dittoHeadersV2, false));
            }
        };
    }

    @Test
    public void retrievePolicy() {
        final Policy policy = createPolicyWithRandomId();
        final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
        final PolicyCommand<?> retrievePolicyCommand =
                RetrievePolicy.of(policy.getEntityId().orElse(null), dittoHeadersV2);
        final PolicyQueryCommandResponse<?> expectedResponse =
                retrievePolicyResponse(incrementRevision(policy, 1), retrievePolicyCommand.getDittoHeaders());

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(this, policy);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                underTest.tell(retrievePolicyCommand, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    @Test
    public void sudoRetrievePolicy() {
        final Policy policy = createPolicyWithRandomId();
        final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);

        final SudoRetrievePolicy sudoRetrievePolicyCommand =
                SudoRetrievePolicy.of(policy.getEntityId().orElseThrow(NoSuchElementException::new), dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(this, policy);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                underTest.tell(sudoRetrievePolicyCommand, getRef());
                expectMsgClass(SudoRetrievePolicyResponse.class);
            }
        };
    }

    @Test
    public void deletePolicy() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().orElse(null))
                        .isEqualEqualToButModified(policy);

                final PolicyId policyId = policy.getEntityId().orElseThrow(NoSuchElementException::new);
                final DeletePolicy deletePolicy = DeletePolicy.of(policyId, dittoHeadersV2);
                policyPersistenceActor.tell(deletePolicy, getRef());
                expectMsgEquals(DeletePolicyResponse.of(policyId, dittoHeadersV2));
            }
        };
    }

    @Test
    public void createPolicyEntry() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final PolicyEntry policyEntryToAdd =
                        PoliciesModelFactory.newPolicyEntry(Label.of("anotherLabel"),
                                POLICY_SUBJECTS, POLICY_RESOURCES_ALL);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policyId, policyEntryToAdd, headersMockWithOtherAuth);
                underTest.tell(modifyPolicyEntry, getRef());
                expectMsgEquals(
                        modifyPolicyEntryResponse(policyId, policyEntryToAdd, headersMockWithOtherAuth, true));
                final RetrievePolicyEntry retrievePolicyEntry =
                        RetrievePolicyEntry.of(policyId, policyEntryToAdd.getLabel(), headersMockWithOtherAuth);
                underTest.tell(retrievePolicyEntry, getRef());
                expectMsgEquals(retrievePolicyEntryResponse(policyId, policyEntryToAdd, headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void modifyPolicyEntry() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject newSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "anotherOne");
                final PolicyEntry policyEntryToModify = PoliciesModelFactory.newPolicyEntry(POLICY_LABEL,
                        Subjects.newInstance(POLICY_SUBJECT, newSubject), POLICY_RESOURCES_ALL);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policyId, policyEntryToModify, headersMockWithOtherAuth);
                underTest.tell(modifyPolicyEntry, getRef());
                expectMsgEquals(
                        modifyPolicyEntryResponse(policyId, policyEntryToModify, headersMockWithOtherAuth, false));

                final RetrievePolicyEntry retrievePolicyEntry =
                        RetrievePolicyEntry.of(policyId, policyEntryToModify.getLabel(), headersMockWithOtherAuth);
                underTest.tell(retrievePolicyEntry, getRef());
                expectMsgEquals(retrievePolicyEntryResponse(policyId, policyEntryToModify, headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void tryToModifyPolicyEntryWithInvalidPermissions() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject newSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "anotherOne");

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final PolicyEntry policyEntryToModify = PoliciesModelFactory.newPolicyEntry(POLICY_LABEL,
                        Subjects.newInstance(POLICY_SUBJECT, newSubject), POLICY_RESOURCES_READ);

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policyId, policyEntryToModify, headersMockWithOtherAuth);
                underTest.tell(modifyPolicyEntry, getRef());
                expectMsgClass(PolicyEntryModificationInvalidException.class);
            }
        };
    }

    @Test
    public void modifyPolicyEntrySoThatPolicyGetsTooLarge() {
        new TestKit(actorSystem) {
            {
                final PolicyBuilder policyBuilder = Policy.newBuilder(PolicyId.of("new", "policy"));
                int i = 0;
                Policy policy;
                do {
                    policyBuilder.forLabel("ENTRY-NO" + i)
                            .setSubject("nginx:ditto", SubjectType.UNKNOWN)
                            .setGrantedPermissions("policy", "/", "READ", "WRITE")
                            .setGrantedPermissions("thing", "/", "READ", "WRITE");
                    policy = policyBuilder.build();
                    i++;
                } while (policy.toJsonString().length() < POLICY_SIZE_LIMIT_BYTES);

                policy = policy.removeEntry("ENTRY-NO" + (i - 1));

                final ActorRef underTest = createPersistenceActorFor(this, policy);

                // creating the Policy should be possible as we are below the limit:
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final PolicyEntry policyEntry = Policy.newBuilder(PolicyId.of("new", "policy"))
                        .forLabel("TEST")
                        .setSubject("nginx:ditto", SubjectType.UNKNOWN)
                        .setSubject("nginx:ditto1", SubjectType.UNKNOWN)
                        .setSubject("nginx:ditto2", SubjectType.UNKNOWN)
                        .setSubject("nginx:ditto3", SubjectType.UNKNOWN)
                        .setGrantedPermissions("policy", "/", "READ", "WRITE")
                        .build()
                        .getEntryFor("TEST")
                        .get();

                final ModifyPolicyEntry command =
                        ModifyPolicyEntry.of(policy.getEntityId().get(), policyEntry, DittoHeaders.empty());

                // but modifying the policy entry which would cause the Policy to exceed the limit should not be allowed:
                underTest.tell(command, getRef());
                expectMsgClass(PolicyTooLargeException.class);
            }
        };
    }

    @Test
    public void removePolicyEntry() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth);
                underTest.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth));

                final RetrievePolicyEntry retrievePolicyEntry =
                        RetrievePolicyEntry.of(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth);
                final PolicyEntryNotAccessibleException expectedResponse = PolicyEntryNotAccessibleException
                        .newBuilder(retrievePolicyEntry.getEntityId(), retrievePolicyEntry.getLabel())
                        .dittoHeaders(retrievePolicyEntry.getDittoHeaders())
                        .build();
                underTest.tell(retrievePolicyEntry, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    @Test
    public void tryToRemoveLastPolicyEntry() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policyId, POLICY_LABEL, headersMockWithOtherAuth);
                underTest.tell(deletePolicyEntry, getRef());
                expectMsgClass(PolicyEntryModificationInvalidException.class);
            }
        };
    }

    @Test
    public void createResource() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Resource resourceToAdd = Resource.newInstance(PoliciesResourceType.policyResource(
                        "/attributes"), EffectedPermissions.newInstance(PoliciesModelFactory.noPermissions(),
                        TestConstants.Policy.PERMISSIONS_ALL));

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyResource modifyResource =
                        ModifyResource.of(policyId, POLICY_LABEL, resourceToAdd, headersMockWithOtherAuth);
                underTest.tell(modifyResource, getRef());
                expectMsgEquals(
                        modifyResourceResponse(policyId, resourceToAdd, POLICY_LABEL, headersMockWithOtherAuth, true));

                final RetrieveResource retrieveResource =
                        RetrieveResource.of(policyId, POLICY_LABEL, resourceToAdd.getResourceKey(),
                                headersMockWithOtherAuth);
                underTest.tell(retrieveResource, getRef());
                expectMsgEquals(
                        retrieveResourceResponse(policyId, POLICY_LABEL, resourceToAdd, headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void modifyResource() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Resource resourceToModify = Resource.newInstance(PoliciesResourceType.policyResource(
                        POLICY_RESOURCE_PATH), EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL,
                        PoliciesModelFactory.noPermissions()));

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyResource modifyResource =
                        ModifyResource.of(policyId, ANOTHER_POLICY_LABEL, resourceToModify, headersMockWithOtherAuth);
                underTest.tell(modifyResource, getRef());
                expectMsgEquals(modifyResourceResponse(policyId, resourceToModify, ANOTHER_POLICY_LABEL,
                        headersMockWithOtherAuth, false));

                final RetrieveResource retrieveResource = RetrieveResource.of(policyId, ANOTHER_POLICY_LABEL,
                        resourceToModify.getResourceKey(), headersMockWithOtherAuth);
                underTest.tell(retrieveResource, getRef());
                expectMsgEquals(retrieveResourceResponse(policyId, ANOTHER_POLICY_LABEL, resourceToModify,
                        headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void tryToModifyResource() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Resource resourceToModify = Resource.newInstance(
                        PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                        EffectedPermissions.newInstance(PoliciesModelFactory.noPermissions(),
                                TestConstants.Policy.PERMISSIONS_ALL));

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyResource modifyResource =
                        ModifyResource.of(policyId, ANOTHER_POLICY_LABEL, resourceToModify, headersMockWithOtherAuth);
                underTest.tell(modifyResource, getRef());
                expectMsgClass(PolicyEntryModificationInvalidException.class);
            }
        };
    }

    @Test
    public void removeResource() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeleteResource deleteResource =
                        DeleteResource.of(policyId, ANOTHER_POLICY_LABEL,
                                PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                                headersMockWithOtherAuth);
                underTest.tell(deleteResource, getRef());
                expectMsgEquals(DeleteResourceResponse.of(policyId, ANOTHER_POLICY_LABEL,
                        PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH), headersMockWithOtherAuth));

                final RetrieveResource retrieveResource =
                        RetrieveResource.of(policyId, ANOTHER_POLICY_LABEL,
                                PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                                headersMockWithOtherAuth);
                final ResourceNotAccessibleException expectedResponse = ResourceNotAccessibleException
                        .newBuilder(retrieveResource.getEntityId(),
                                retrieveResource.getLabel(),
                                retrieveResource.getResourceKey().toString())
                        .dittoHeaders(retrieveResource.getDittoHeaders())
                        .build();
                underTest.tell(retrieveResource, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    @Test
    public void createSubject() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject subjectToAdd =
                        Subject.newInstance(SubjectIssuer.GOOGLE, "anotherSubjectId");

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifySubject modifySubject =
                        ModifySubject.of(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth);
                underTest.tell(modifySubject, getRef());
                expectMsgEquals(
                        modifySubjectResponse(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth, true));

                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, POLICY_LABEL, subjectToAdd.getId(), headersMockWithOtherAuth);
                final RetrieveSubjectResponse expectedResponse =
                        retrieveSubjectResponse(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth);
                underTest.tell(retrieveSubject, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    @Test
    public void modifySubject() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject subjectToModify =
                        Subject.newInstance(POLICY_SUBJECT_ID, SUBJECT_TYPE);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifySubject modifySubject =
                        ModifySubject.of(policyId, POLICY_LABEL, subjectToModify, headersMockWithOtherAuth);
                underTest.tell(modifySubject, getRef());
                expectMsgEquals(modifySubjectResponse(policyId, POLICY_LABEL, subjectToModify, headersMockWithOtherAuth,
                        false));

                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, POLICY_LABEL, subjectToModify.getId(), headersMockWithOtherAuth);
                underTest.tell(retrieveSubject, getRef());
                expectMsgEquals(
                        retrieveSubjectResponse(policyId, POLICY_LABEL, subjectToModify, headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void removeSubject() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeleteSubject deleteSubject =
                        DeleteSubject.of(policyId, ANOTHER_POLICY_LABEL, POLICY_SUBJECT_ID, headersMockWithOtherAuth);
                underTest.tell(deleteSubject, getRef());
                expectMsgEquals(
                        DeleteSubjectResponse.of(policyId, ANOTHER_POLICY_LABEL, POLICY_SUBJECT_ID,
                                headersMockWithOtherAuth));

                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, ANOTHER_POLICY_LABEL, POLICY_SUBJECT_ID, headersMockWithOtherAuth);
                final SubjectNotAccessibleException expectedResponse = SubjectNotAccessibleException
                        .newBuilder(retrieveSubject.getEntityId(),
                                retrieveSubject.getLabel(),
                                retrieveSubject.getSubjectId())
                        .dittoHeaders(retrieveSubject.getDittoHeaders())
                        .build();
                underTest.tell(retrieveSubject, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    @Test
    public void createSubjectWithExpiry() {
        new TestKit(actorSystem) {
            {
                waitPastTimeBorder();
                final Policy policy = createPolicyWithRandomId();
                final Instant expiryInstant = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                        .plus(2, ChronoUnit.SECONDS)
                        .atZone(ZoneId.systemDefault()).toInstant();
                final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance(expiryInstant);
                final SubjectAnnouncement subjectAnnouncement =
                        SubjectAnnouncement.of(DittoDuration.parseDuration("1s"), false);
                final Subject subjectToAdd =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "anotherSubjectId"),
                                SubjectType.GENERATED, subjectExpiry, subjectAnnouncement);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                // GIVEN: a Policy is created without a Subject having an "expiry" date
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                // THEN: a PolicyCreated event should be emitted
                final DistributedPubSubMediator.Publish policyCreatedPublish =
                        pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
                assertThat(policyCreatedPublish.msg()).isInstanceOf(PolicyCreated.class);

                // WHEN: the Policy's subject is modified having an "expiry" in the near future
                final ModifySubject modifySubject =
                        ModifySubject.of(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth);
                underTest.tell(modifySubject, getRef());

                // THEN: a SubjectCreated event should be emitted
                final DistributedPubSubMediator.Publish subjectCreatedPublish =
                        (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                                scala.concurrent.duration.Duration.create(2, "s"),
                                "publish event",
                                publishedPolicyEvent());
                assertThat(subjectCreatedPublish.msg()).isInstanceOf(SubjectCreated.class);

                final long secondsToAdd = 10 - (expiryInstant.getEpochSecond() % 10);
                final Instant expectedRoundedExpiryInstant =
                        expiryInstant.plusSeconds(secondsToAdd); // to next 10s rounded up
                final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance(expectedRoundedExpiryInstant);
                final Subject expectedAdjustedSubjectToAdd = Subject.newInstance(subjectToAdd.getId(),
                        subjectToAdd.getType(), expectedSubjectExpiry, subjectAnnouncement);

                // THEN: the subject expiry should be rounded up to the configured "subject-expiry-granularity"
                //  (10s for this test)
                expectMsgEquals(modifySubjectResponse(policyId, POLICY_LABEL, expectedAdjustedSubjectToAdd,
                        headersMockWithOtherAuth, true));

                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, POLICY_LABEL, subjectToAdd.getId(), headersMockWithOtherAuth);
                final RetrieveSubjectResponse expectedResponse =
                        retrieveSubjectResponse(policyId, POLICY_LABEL, expectedAdjustedSubjectToAdd,
                                headersMockWithOtherAuth);
                underTest.tell(retrieveSubject, getRef());
                expectMsgEquals(expectedResponse);

                // THEN: waiting until the expiry interval should emit a SubjectDeleted event
                final Duration between =
                        Duration.between(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(),
                                expectedRoundedExpiryInstant);
                final long secondsToWaitForSubjectDeletedEvent = between.getSeconds() + 2;
                final DistributedPubSubMediator.Publish policySubjectDeleted =
                        (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                                scala.concurrent.duration.Duration.create(secondsToWaitForSubjectDeletedEvent, "s"),
                                "publish event",
                                publishedPolicyEvent()
                        );
                final Object subjectDeletedMsg = policySubjectDeleted.msg();
                assertThat(subjectDeletedMsg).isInstanceOf(SubjectDeleted.class);
                assertThat(((SubjectDeleted) subjectDeletedMsg).getSubjectId())
                        .isEqualTo(expectedAdjustedSubjectToAdd.getId());

                // THEN: a SubjectDeletionAnnouncement should have been published
                final var announcementCaptor = ArgumentCaptor.forClass(SubjectDeletionAnnouncement.class);
                verify(policyAnnouncementPub).publishWithAcks(announcementCaptor.capture(), any(), any(), any());
                final SubjectDeletionAnnouncement announcement = announcementCaptor.getValue();
                Assertions.assertThat(announcement.getSubjectIds()).containsExactly(subjectToAdd.getId());
                Assertions.assertThat(announcement.getDeleteAt()).isEqualTo(expectedRoundedExpiryInstant);

                // THEN: a PolicyTag should be emitted via pub/sub indicating that the policy enforcer caches should be invalidated
                final DistributedPubSubMediator.Publish policyTagForCacheInvalidation =
                        pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
                final Object policyTagForCacheInvalidationMsg = policyTagForCacheInvalidation.msg();
                assertThat(policyTagForCacheInvalidationMsg).isInstanceOf(PolicyTag.class);
                assertThat(((PolicyTag) policyTagForCacheInvalidationMsg).getEntityId())
                        .isEqualTo(policyId);

                // THEN: retrieving the expired subject should fail
                underTest.tell(retrieveSubject, getRef());
                expectMsgClass(SubjectNotAccessibleException.class);
            }
        };
    }

    @Test
    public void sendAnnouncementAfterDeletion() {
        new TestKit(actorSystem) {
            {
                final SubjectAnnouncement subjectAnnouncement = SubjectAnnouncement.of(null, true);
                final Subject subject1 =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject1"),
                                SubjectType.GENERATED, null, subjectAnnouncement);
                final Subject subject2 =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject2"),
                                SubjectType.GENERATED, null, subjectAnnouncement);
                final Policy policy = createPolicyWithRandomId().toBuilder()
                        .forLabel(POLICY_LABEL)
                        .setSubject(subject1)
                        .forLabel(ANOTHER_POLICY_LABEL)
                        .setSubject(subject2)
                        .build();
                final PolicyId policyId = policy.getEntityId().orElseThrow();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final ActorRef underTest = createPersistenceActorFor(this, policy);

                // GIVEN: a Policy is created
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                expectMsgClass(CreatePolicyResponse.class);

                // WHEN: subject2 is deleted
                final var deletePolicyEntry =
                        DeletePolicyEntry.of(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth);
                underTest.tell(deletePolicyEntry, getRef());
                expectMsgClass(DeletePolicyEntryResponse.class);

                // THEN: announcement is published for subject2
                final var captor = ArgumentCaptor.forClass(SubjectDeletionAnnouncement.class);
                verify(policyAnnouncementPub, timeout(5000)).publishWithAcks(captor.capture(), any(), any(), any());
                final SubjectDeletionAnnouncement announcement2 = captor.getValue();
                Assertions.assertThat(announcement2.getSubjectIds()).containsExactly(subject2.getId());

                // WHEN: policy is deleted
                final var deletePolicy = DeletePolicy.of(policyId, headersMockWithOtherAuth);
                underTest.tell(deletePolicy, getRef());
                expectMsgClass(DeletePolicyResponse.class);

                // THEN: announcement is published for subject1
                verify(policyAnnouncementPub, timeout(5000).times(2))
                        .publishWithAcks(captor.capture(), any(), any(), any());
                final SubjectDeletionAnnouncement announcement1 = captor.getValue();
                Assertions.assertThat(announcement1.getSubjectIds()).containsExactly(subject1.getId());

                // THEN: announcements are published no more than once while the actor is alive.
                verifyNoMoreInteractions(policyAnnouncementPub);
            }
        };
    }

    @Test
    public void sendAnnouncementBeforeExpiry() {
        new TestKit(actorSystem) {
            {
                // Do not wait past time border because no expiration will occur.
                final Policy policy = createPolicyWithRandomId();
                final Instant expiryInstant = Instant.now().plus(Duration.ofHours(2));
                final SubjectExpiry subjectExpiry1 = SubjectExpiry.newInstance(expiryInstant);
                final SubjectExpiry subjectExpiry2 = SubjectExpiry.newInstance(expiryInstant.plus(Duration.ofHours(1)));
                final SubjectAnnouncement subjectAnnouncement =
                        SubjectAnnouncement.of(DittoDuration.parseDuration("10h"), false);
                final Subject subject1 =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject1"),
                                SubjectType.GENERATED, subjectExpiry1, subjectAnnouncement);

                final Subject subject2 =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject2"),
                                SubjectType.GENERATED, subjectExpiry2, subjectAnnouncement);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElseThrow();
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                // GIVEN: a Policy is created
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                expectMsgClass(CreatePolicyResponse.class);

                // WHEN: the Policy's subject is modified having an "expiry" in the far future
                final ModifySubjects modifySubjects =
                        ModifySubjects.of(policyId, ANOTHER_POLICY_LABEL, Subjects.newInstance(subject1, subject2),
                                headersMockWithOtherAuth);
                underTest.tell(modifySubjects, getRef());
                expectMsgClass(ModifySubjectsResponse.class);

                // THEN: announcements are published
                final var captor = ArgumentCaptor.forClass(SubjectDeletionAnnouncement.class);
                verify(policyAnnouncementPub, timeout(5000).times(2))
                        .publishWithAcks(captor.capture(), any(), any(), any());
                for (final var announcement : captor.getAllValues()) {
                    if (announcement.getSubjectIds().contains(subject1.getId())) {
                        Assertions.assertThat(announcement.getDeleteAt())
                                .isAfterOrEqualTo(subjectExpiry1.getTimestamp());
                    } else if (announcement.getSubjectIds().contains(subject2.getId())) {
                        Assertions.assertThat(announcement.getDeleteAt())
                                .isAfterOrEqualTo(subjectExpiry2.getTimestamp());
                    } else {
                        throw new AssertionError("Expect subject deletion announcement for subject1 " +
                                "or subject2, got: " + announcement);
                    }
                }

                // THEN: announcements are published no more than once while the actor is alive.
                verifyNoMoreInteractions(policyAnnouncementPub);
            }
        };
    }

    @Test
    public void createPolicyWith2SubjectsWithExpiry() {
        new TestKit(actorSystem) {{
            waitPastTimeBorder();
            final Instant expiryInstant = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                    .plus(2, ChronoUnit.SECONDS)
                    .atZone(ZoneId.systemDefault()).toInstant();
            final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance(expiryInstant);
            final Subject subject1 =
                    Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject1"),
                            SubjectType.GENERATED, subjectExpiry);
            final Subject subject2 =
                    Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject2"),
                            SubjectType.GENERATED, subjectExpiry);
            final Subject subject3 =
                    Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject3"), SubjectType.GENERATED);
            final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy:id"))
                    .forLabel(POLICY_LABEL)
                    .setSubjects(Subjects.newInstance(subject1, subject2, subject3))
                    .setResources(POLICY_RESOURCES_ALL)
                    .build();

            final ActorRef underTest = createPersistenceActorFor(this, policy);

            // GIVEN: a Policy is created with 2 subjects having an "expiry" date
            final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
            underTest.tell(createPolicyCommand, getRef());
            expectMsgClass(CreatePolicyResponse.class);

            // THEN: a PolicyCreated event should be emitted
            final DistributedPubSubMediator.Publish policyCreatedPublish =
                    pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(policyCreatedPublish.msg()).isInstanceOf(PolicyCreated.class);
            assertThat(((PolicyCreated) policyCreatedPublish.msg()).getRevision()).isEqualTo(1L);

            // THEN: subject1 is deleted after expiry
            final long secondsToAdd = 10 - (expiryInstant.getEpochSecond() % 10);
            final Instant expectedRoundedExpiryInstant = expiryInstant.plusSeconds(secondsToAdd);
            final Duration between =
                    Duration.between(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(),
                            expectedRoundedExpiryInstant);
            final long secondsToWaitForSubjectDeletedEvent = between.getSeconds() + 2;
            final DistributedPubSubMediator.Publish subject1Deleted =
                    (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                            scala.concurrent.duration.Duration.create(secondsToWaitForSubjectDeletedEvent, "s"),
                            "publish event",
                            publishedPolicyEvent());
            assertThat(subject1Deleted.msg()).isInstanceOf(SubjectDeleted.class);
            assertThat(((SubjectDeleted) subject1Deleted.msg()).getSubjectId())
                    .isIn(subject1.getId(), subject2.getId());
            assertThat(((SubjectDeleted) subject1Deleted.msg()).getRevision()).isEqualTo(2L);
            assertThat(pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class).msg())
                    .isInstanceOf(PolicyTag.class);

            // THEN: subject2 is deleted immediately
            final DistributedPubSubMediator.Publish subject2Deleted =
                    (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                            scala.concurrent.duration.Duration.create(secondsToWaitForSubjectDeletedEvent, "s"),
                            "publish event",
                            publishedPolicyEvent());
            assertThat(subject2Deleted.msg()).isInstanceOf(SubjectDeleted.class);
            assertThat(((SubjectDeleted) subject2Deleted.msg()).getSubjectId())
                    .isIn(subject1.getId(), subject2.getId());
            assertThat(((SubjectDeleted) subject2Deleted.msg()).getRevision()).isEqualTo(3L);

            // THEN: the policy has only subject3 left.
            underTest.tell(RetrievePolicy.of(policy.getEntityId().orElseThrow(), DittoHeaders.empty()), getRef());
            final RetrievePolicyResponse response = expectMsgClass(RetrievePolicyResponse.class);
            Assertions.assertThat(response.getPolicy().getEntryFor(POLICY_LABEL).orElseThrow().getSubjects())
                    .containsOnly(subject3);
        }};
    }

    private PartialFunction<Object, Object> publishedPolicyEvent() {
        return PartialFunction.fromFunction(msg ->
                msg instanceof DistributedPubSubMediator.Publish publish &&
                        publish.topic().startsWith(PolicyEvent.TYPE_PREFIX));
    }

    @Test
    public void impossibleToMakePolicyInvalidByExpiringSubjects() {
        new TestKit(actorSystem) {{
            final Instant futureInstant = Instant.now().plus(Duration.ofDays(1L));
            final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance(futureInstant);
            final Subject subject1 =
                    Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject1"),
                            SubjectType.GENERATED, subjectExpiry);
            final Subject subject2 =
                    Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject2"),
                            SubjectType.GENERATED, subjectExpiry);
            final Subject subject3 =
                    Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject3"),
                            SubjectType.GENERATED);
            final Subject subject4 =
                    Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "subject4"),
                            SubjectType.GENERATED,
                            SubjectExpiry.newInstance(Instant.EPOCH));
            final PolicyId policyId = PolicyId.of("policy:id");
            final DittoHeaders headers = DittoHeaders.empty();

            final Policy validPolicy = PoliciesModelFactory.newPolicyBuilder(policyId)
                    .forLabel(POLICY_LABEL)
                    .setSubjects(Subjects.newInstance(subject3))
                    .setResources(POLICY_RESOURCES_ALL)
                    .build();

            final Policy policyWithExpiredSubject = PoliciesModelFactory.newPolicyBuilder(policyId)
                    .forLabel(POLICY_LABEL)
                    .setSubjects(Subjects.newInstance(subject1, subject2, subject3, subject4))
                    .setResources(POLICY_RESOURCES_ALL)
                    .build();

            final Subjects expiringSubjects = Subjects.newInstance(subject1, subject2);
            final PolicyEntry expiringEntry =
                    PoliciesModelFactory.newPolicyEntry(POLICY_LABEL, expiringSubjects, POLICY_RESOURCES_ALL);
            final Policy policyWithoutPermanentSubject = PoliciesModelFactory.newPolicyBuilder(policyId)
                    .set(expiringEntry)
                    .build();

            // GIVEN: policy is not created
            final ActorRef underTest = createPersistenceActorFor(this, policyId);

            // WHEN/THEN CreatePolicy fails if policy contains expired subject
            underTest.tell(CreatePolicy.of(policyWithExpiredSubject, headers), getRef());
            expectMsgClass(SubjectExpiryInvalidException.class);

            // GIVEN: policy is created
            underTest.tell(CreatePolicy.of(validPolicy, headers), getRef());
            expectMsgClass(CreatePolicyResponse.class);

            // WHEN/THEN ModifyPolicy fails if policy contains only expiring subjects afterwards
            underTest.tell(ModifyPolicy.of(policyId, policyWithoutPermanentSubject, headers), getRef());
            expectMsgClass(PolicyModificationInvalidException.class);

            // WHEN/THEN ModifyPolicyEntry fails if policy contains only expiring subjects afterwards
            underTest.tell(ModifyPolicyEntry.of(policyId, expiringEntry, headers), getRef());
            expectMsgClass(PolicyEntryModificationInvalidException.class);

            // WHEN/THEN ModifySubjects fails if policy contains only expiring subjects afterwards
            underTest.tell(ModifySubjects.of(policyId, POLICY_LABEL, expiringSubjects, headers), getRef());
            expectMsgClass(PolicyEntryModificationInvalidException.class);

            // WHEN/THEN DeleteSubject fails if policy contains only expiring subjects afterwards
            underTest.tell(DeleteSubject.of(policyId, POLICY_LABEL, subject3.getId(), headers), getRef());
            expectMsgClass(PolicyEntryModificationInvalidException.class);
        }};
    }

    @Test
    public void recoverPolicyCreated() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                // restart
                terminate(this, policyPersistenceActor);
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(this, policy);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getEntityId().orElse(null), dittoHeadersV2);

                final RetrievePolicyResponse expectedResponse =
                        retrievePolicyResponse(incrementRevision(policy, 1), dittoHeadersV2);

                Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    policyPersistenceActorRecovered.tell(retrievePolicy, getRef());
                    expectMsgEquals(expectedResponse);
                });

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    @Test
    public void recoverPolicyDeleted() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicy deletePolicy = DeletePolicy.of(policy.getEntityId().get(), dittoHeadersV2);
                policyPersistenceActor.tell(deletePolicy, getRef());
                expectMsgEquals(DeletePolicyResponse.of(policy.getEntityId().get(), dittoHeadersV2));

                // restart
                terminate(this, policyPersistenceActor);
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(this, policy);

                // A deleted Policy cannot be retrieved anymore.
                final RetrievePolicy retrievePolicy = RetrievePolicy.of(policy.getEntityId().get(), dittoHeadersV2);
                policyPersistenceActorRecovered.tell(retrievePolicy, getRef());
                expectMsgClass(PolicyNotAccessibleException.class);

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    @Test
    public void recoverPolicyEntryModified() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                // event published with group:
                final DistributedPubSubMediator.Publish policyCreatedPublishSecond =
                        pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
                assertThat(policyCreatedPublishSecond.msg()).isInstanceOf(PolicyCreated.class);

                final Subject newSubject =
                        Subject.newInstance(SubjectIssuer.GOOGLE, "anotherOne");
                final Resource newResource =
                        Resource.newInstance(PoliciesResourceType.policyResource("/attributes"), EffectedPermissions
                                .newInstance(PoliciesModelFactory.noPermissions(),
                                        TestConstants.Policy.PERMISSIONS_ALL));
                final PolicyEntry policyEntry = PoliciesModelFactory.newPolicyEntry(Label.of("anotherLabel"),
                        Subjects.newInstance(newSubject), Resources.newInstance(newResource));

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policy.getEntityId().orElse(null), policyEntry, dittoHeadersV2);
                policyPersistenceActor.tell(modifyPolicyEntry, getRef());
                expectMsgEquals(modifyPolicyEntryResponse(policy.getEntityId().get(), policyEntry,
                        dittoHeadersV2, true));

                // event published with group:
                final DistributedPubSubMediator.Publish policyEntryModifiedPublishSecond =
                        (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                                scala.concurrent.duration.Duration.create(2, "s"),
                                "publish event",
                                publishedPolicyEvent()
                        );
                assertThat(policyEntryModifiedPublishSecond.msg()).isInstanceOf(PolicyEntryCreated.class);

                // restart
                terminate(this, policyPersistenceActor);
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(this, policy);

                final Policy policyWithUpdatedPolicyEntry = policy.setEntry(policyEntry);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policyWithUpdatedPolicyEntry.getEntityId().orElse(null), dittoHeadersV2);
                final RetrievePolicyResponse expectedResponse =
                        retrievePolicyResponse(incrementRevision(policyWithUpdatedPolicyEntry, 2), dittoHeadersV2);

                Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    policyPersistenceActorRecovered.tell(retrievePolicy, getRef());
                    expectMsgEquals(expectedResponse);
                });

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    @Test
    public void recoverPolicyEntryDeleted() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policy.getEntityId().get(), ANOTHER_POLICY_LABEL, dittoHeadersV2);
                policyPersistenceActor.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policy.getEntityId().orElse(null), ANOTHER_POLICY_LABEL,
                        dittoHeadersV2));

                // restart
                terminate(this, policyPersistenceActor);
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(this, policy);

                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getEntityId().get(), dittoHeadersV2);
                final RetrievePolicyResponse expectedResponse =
                        retrievePolicyResponse(incrementRevision(policy, 2).removeEntry(ANOTHER_POLICY_LABEL),
                                dittoHeadersV2);

                Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    policyPersistenceActorRecovered.tell(retrievePolicy, getRef());
                    expectMsgEquals(expectedResponse);
                });

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    @Test
    public void ensureSubjectExpiryIsCleanedUpAfterRecovery() {
        new TestKit(actorSystem) {
            {
                waitPastTimeBorder();
                final Policy policy = createPolicyWithRandomId();
                final PolicyId policyId = policy.getEntityId().orElseThrow();
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                final Instant expiryInstant = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                        .plus(2, ChronoUnit.SECONDS)
                        .atZone(ZoneId.systemDefault()).toInstant();
                final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance(expiryInstant);
                final Subject expiringSubject =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "about-to-expire"),
                                SubjectType.GENERATED, subjectExpiry);
                final Policy policyWithExpiringSubject = policy.toBuilder()
                        .setSubjectFor(TestConstants.Policy.SUPPORT_LABEL, expiringSubject)
                        .build();

                final long secondsToAdd = 10 - (expiryInstant.getEpochSecond() % 10);
                final Instant expectedRoundedExpiryInstant =
                        expiryInstant.plusSeconds(secondsToAdd); // to next 10s rounded up
                final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance(expectedRoundedExpiryInstant);
                final Subject expectedAdjustedSubjectToAdd = Subject.newInstance(expiringSubject.getId(),
                        expiringSubject.getType(), expectedSubjectExpiry);

                final Policy adjustedPolicyWithExpiringSubject = policyWithExpiringSubject.toBuilder()
                        .setSubjectFor(TestConstants.Policy.SUPPORT_LABEL, expectedAdjustedSubjectToAdd)
                        .build();

                // WHEN: a new Policy is created containing an expiring subject
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policyWithExpiringSubject, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());

                // THEN: the response should contain the adjusted (rounded up) expiry time
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(adjustedPolicyWithExpiringSubject);

                // THEN: the created event should be emitted
                final DistributedPubSubMediator.Publish policyCreatedPublishSecond =
                        pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
                assertThat(policyCreatedPublishSecond.msg()).isInstanceOf(PolicyCreated.class);

                // WHEN: now the persistence actor is restarted
                terminate(this, underTest);
                final ActorRef underTestRecovered = createPersistenceActorFor(this, policy);

                // AND WHEN: the policy is retrieved (and restored as a consequence)
                final RetrievePolicy retrievePolicy = RetrievePolicy.of(policyId, dittoHeadersV2);
                final RetrievePolicyResponse expectedResponse =
                        retrievePolicyResponse(incrementRevision(adjustedPolicyWithExpiringSubject, 1), dittoHeadersV2);

                Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    underTestRecovered.tell(retrievePolicy, getRef());
                    expectMsgEquals(expectedResponse);
                });
                assertThat(getLastSender()).isEqualTo(underTestRecovered);

                // THEN: waiting until the expiry interval should emit a SubjectDeleted event
                final Duration between =
                        Duration.between(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(),
                                expectedRoundedExpiryInstant);
                final long secondsToWaitForSubjectDeletedEvent = between.getSeconds() + 2;
                final DistributedPubSubMediator.Publish policySubjectDeleted =
                        (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                                scala.concurrent.duration.Duration.create(secondsToWaitForSubjectDeletedEvent, "s"),
                                "publish event",
                                publishedPolicyEvent()
                        );
                final Object subjectDeletedMsg = policySubjectDeleted.msg();
                assertThat(subjectDeletedMsg).isInstanceOf(SubjectDeleted.class);
                assertThat(((SubjectDeleted) subjectDeletedMsg).getSubjectId())
                        .isEqualTo(expectedAdjustedSubjectToAdd.getId());

                // THEN: a PolicyTag should be emitted via pub/sub indicating that the policy enforcer caches should be invalidated
                final DistributedPubSubMediator.Publish policyTagForCacheInvalidation =
                        pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
                final Object policyTagForCacheInvalidationMsg = policyTagForCacheInvalidation.msg();
                assertThat(policyTagForCacheInvalidationMsg).isInstanceOf(PolicyTag.class);
                assertThat(((PolicyTag) policyTagForCacheInvalidationMsg).getEntityId())
                        .isEqualTo(policyId);

                // THEN: retrieving the expired subject should fail
                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, POLICY_LABEL, expiringSubject.getId(), dittoHeadersV2);
                underTestRecovered.tell(retrieveSubject, getRef());
                expectMsgClass(SubjectNotAccessibleException.class);
            }
        };
    }

    @Test
    public void ensureExpiredSubjectIsRemovedDuringRecovery() throws InterruptedException {
        new TestKit(actorSystem) {
            {
                waitPastTimeBorder();
                final Policy policy = createPolicyWithRandomId();
                final PolicyId policyId = policy.getEntityId().orElseThrow();
                final ClusterShardingSettings shardingSettings =
                        ClusterShardingSettings.apply(actorSystem).withRole("policies");
                final var box = new AtomicReference<ActorRef>();
                final ActorRef announcementManager = createAnnouncementManager(policyId, box::get);
                final Props props = PolicyPersistenceActor.propsForTests(policyId, Mockito.mock(MongoReadJournal.class),
                        pubSubMediator, announcementManager,
                        actorSystem);
                final Cluster cluster = Cluster.get(actorSystem);
                cluster.join(cluster.selfAddress());
                final ActorRef underTest = ClusterSharding.get(actorSystem)
                        .start(PoliciesMessagingConstants.SHARD_REGION, props, shardingSettings,
                                ShardRegionExtractor.of(30, actorSystem));
                box.set(underTest);

                final Instant expiryInstant = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                        .plus(2, ChronoUnit.SECONDS)
                        .atZone(ZoneId.systemDefault()).toInstant();
                final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance(expiryInstant);
                final SubjectAnnouncement subjectAnnouncement = SubjectAnnouncement.of(null, true);
                final Subject expiringSubject =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "about-to-expire"),
                                SubjectType.GENERATED, subjectExpiry, subjectAnnouncement);
                final Policy policyWithExpiringSubject = policy.toBuilder()
                        .setSubjectFor(TestConstants.Policy.SUPPORT_LABEL, expiringSubject)
                        .build();

                final long secondsToAdd = 10 - (expiryInstant.getEpochSecond() % 10);
                final Instant expectedRoundedExpiryInstant = (secondsToAdd == 10) ? expiryInstant :
                        expiryInstant.plusSeconds(secondsToAdd); // to next 10s rounded up
                final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance(expectedRoundedExpiryInstant);
                final Subject expectedAdjustedSubjectToAdd = Subject.newInstance(expiringSubject.getId(),
                        expiringSubject.getType(), expectedSubjectExpiry, subjectAnnouncement);

                final Policy adjustedPolicyWithExpiringSubject = policyWithExpiringSubject.toBuilder()
                        .setSubjectFor(TestConstants.Policy.SUPPORT_LABEL, expectedAdjustedSubjectToAdd)
                        .build();

                // WHEN: a new Policy is created containing an expiring subject
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policyWithExpiringSubject, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());

                // THEN: the response should contain the adjusted (rounded up) expiry time
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                final ActorRef firstPersistenceActor = getLastSender();
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().orElseThrow())
                        .isEqualEqualToButModified(adjustedPolicyWithExpiringSubject);

                // THEN: the created event should be emitted
                final DistributedPubSubMediator.Publish policyCreatedPublishSecond =
                        (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                                scala.concurrent.duration.Duration.create(2, "s"),
                                "publish event",
                                publishedPolicyEvent()
                        );
                assertThat(policyCreatedPublishSecond.msg()).isInstanceOf(PolicyCreated.class);

                // WHEN: now the persistence actor is terminated
                firstPersistenceActor.tell(PoisonPill.getInstance(), ActorRef.noSender());

                // WHEN: it is waited until the subject expired
                final Duration between =
                        Duration.between(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(),
                                expectedRoundedExpiryInstant);
                TimeUnit.MILLISECONDS.sleep(between.toMillis() + 200L);

                // AND WHEN: the policy is retrieved (and restored as a consequence)
                final SudoRetrievePolicy sudoRetrievePolicy = SudoRetrievePolicy.of(policyId, dittoHeadersV2);
                underTest.tell(sudoRetrievePolicy, getRef());
                expectMsgClass(SudoRetrievePolicyResponse.class);
                final ActorRef secondPersistenceActor = getLastSender();
                box.set(secondPersistenceActor);

                // THEN: the restored policy persistence actor has a different reference and the same actor path
                assertThat(secondPersistenceActor).isNotEqualTo(firstPersistenceActor);
                assertThat(secondPersistenceActor.path()).isEqualTo(firstPersistenceActor.path());

                // THEN: SubjectDeletionAnnouncement is published
                final var announcementCaptor = ArgumentCaptor.forClass(SubjectDeletionAnnouncement.class);
                verify(policyAnnouncementPub, timeout(5000))
                        .publishWithAcks(announcementCaptor.capture(), any(), any(), any());
                final var announcement = announcementCaptor.getValue();
                Assertions.assertThat(announcement.getSubjectIds()).containsExactly(expiringSubject.getId());

                // THEN: returned policy via SudoRetrievePolicyResponse does no longer contain the already expired subject:
                underTest.tell(sudoRetrievePolicy, getRef());
                final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                        expectMsgClass(SudoRetrievePolicyResponse.class);
                final Policy expectedPolicyWithoutExpiredSubject = incrementRevision(policy.toBuilder()
                        .removeSubjectFor(TestConstants.Policy.SUPPORT_LABEL, expiringSubject.getId())
                        .build(), 2);
                assertThat(sudoRetrievePolicyResponse.getPolicy().getEntriesSet())
                        .isEqualTo(expectedPolicyWithoutExpiredSubject.getEntriesSet());
                assertThat(sudoRetrievePolicyResponse.getPolicy().getRevision())
                        .isEqualTo(expectedPolicyWithoutExpiredSubject.getRevision());

                // THEN: waiting until the expiry interval should emit a SubjectDeleted event
                final DistributedPubSubMediator.Publish policySubjectDeleted =
                        (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                                scala.concurrent.duration.Duration.create(2, "s"),
                                "publish event",
                                publishedPolicyEvent());
                final Object subjectDeletedMsg = policySubjectDeleted.msg();
                assertThat(subjectDeletedMsg).isInstanceOf(SubjectDeleted.class);
                assertThat(((SubjectDeleted) subjectDeletedMsg).getSubjectId())
                        .isEqualTo(expectedAdjustedSubjectToAdd.getId());

                // THEN: a PolicyTag should be emitted via pub/sub indicating that the policy enforcer caches should be invalidated
                final DistributedPubSubMediator.Publish policyTagForCacheInvalidation =
                        pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
                final Object policyTagForCacheInvalidationMsg = policyTagForCacheInvalidation.msg();
                assertThat(policyTagForCacheInvalidationMsg).isInstanceOf(PolicyTag.class);
                assertThat(((PolicyTag) policyTagForCacheInvalidationMsg).getEntityId())
                        .isEqualTo(policyId);

                // THEN: retrieving the expired subject should fail
                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, POLICY_LABEL, expiringSubject.getId(), dittoHeadersV2);
                underTest.tell(retrieveSubject, getRef());
                expectMsgClass(SubjectNotAccessibleException.class);
            }
        };
    }

    @Test
    public void ensureSequenceNumberCorrectness() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                // create the policy - results in sequence number 1
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                // modify the policy's entries - results in sequence number 2
                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policy.getEntityId().orElse(null), ANOTHER_POLICY_LABEL, dittoHeadersV2);
                policyPersistenceActor.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policy.getEntityId().orElse(null), ANOTHER_POLICY_LABEL,
                        dittoHeadersV2));

                // retrieve the policy's sequence number
                final long versionExpected = 2;
                final Policy policyExpected = PoliciesModelFactory.newPolicyBuilder(policy)
                        .remove(ANOTHER_POLICY_LABEL)
                        .setRevision(versionExpected)
                        .build();
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getEntityId().orElse(null), dittoHeadersV2);
                policyPersistenceActor.tell(retrievePolicy, getRef());
                expectMsgEquals(retrievePolicyResponse(policyExpected, retrievePolicy.getDittoHeaders()));
            }
        };
    }

    @Test
    public void ensureSequenceNumberCorrectnessAfterRecovery() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

                // create the policy - results in sequence number 1
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                // modify the policy's entries - results in sequence number 2
                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policy.getEntityId().orElse(null), ANOTHER_POLICY_LABEL, dittoHeadersV2);
                policyPersistenceActor.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policy.getEntityId().orElse(null), ANOTHER_POLICY_LABEL,
                        dittoHeadersV2));

                // retrieve the policy's sequence number from recovered actor
                final long versionExpected = 2;
                final Policy policyExpected = PoliciesModelFactory.newPolicyBuilder(policy)
                        .remove(ANOTHER_POLICY_LABEL)
                        .setRevision(versionExpected)
                        .build();

                // restart
                terminate(this, policyPersistenceActor);
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(this, policy);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getEntityId().orElse(null), dittoHeadersV2);

                final RetrievePolicyResponse expectedResponse =
                        retrievePolicyResponse(policyExpected, retrievePolicy.getDittoHeaders());

                Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    policyPersistenceActorRecovered.tell(retrievePolicy, getRef());
                    expectMsgEquals(expectedResponse);
                });

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    @Test
    public void testPolicyPersistenceActorRespondsToCleanupCommandInCreatedState() {
        new TestKit(actorSystem) {{
            final Policy policy = createPolicyWithRandomId();
            final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

            final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
            policyPersistenceActor.tell(createPolicyCommand, getRef());
            final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
            DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                    .isEqualEqualToButModified(policy);

            final PolicyId policyId = policy.getEntityId().orElseThrow(IllegalStateException::new);
            policyPersistenceActor.tell(CleanupPersistence.of(policyId, DittoHeaders.empty()), getRef());
            expectMsg(CleanupPersistenceResponse.success(policyId, DittoHeaders.empty()));
        }};
    }

    @Test
    public void testPolicyPersistenceActorRespondsToCleanupCommandInDeletedState() {
        new TestKit(actorSystem) {{
            final Policy policy = createPolicyWithRandomId();
            final ActorRef policyPersistenceActor = createPersistenceActorFor(this, policy);

            final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
            policyPersistenceActor.tell(createPolicyCommand, getRef());
            final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
            DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                    .isEqualEqualToButModified(policy);

            final DeletePolicy deletePolicyCommand =
                    DeletePolicy.of(policy.getEntityId().orElseThrow(() -> new IllegalStateException("no id")),
                            dittoHeadersV2);
            policyPersistenceActor.tell(deletePolicyCommand, getRef());
            expectMsgClass(DeletePolicyResponse.class);

            final PolicyId policyId = policy.getEntityId().orElseThrow(IllegalStateException::new);
            policyPersistenceActor.tell(CleanupPersistence.of(policyId, DittoHeaders.empty()), getRef());
            expectMsg(CleanupPersistenceResponse.success(policyId, DittoHeaders.empty()));
        }};
    }

    @Test
    public void checkForActivityOfNonexistentPolicy() {
        new TestKit(actorSystem) {
            {
                // GIVEN: a PolicyPersistenceActor is created in a parent that forwards all messages to us
                final PolicyId policyId = PolicyId.of("test.ns", "nonexistent.policy");
                final var box = new AtomicReference<ActorRef>();
                final ActorRef announcementManager = createAnnouncementManager(policyId, box::get);
                final Props persistentActorProps =
                        PolicyPersistenceActor.propsForTests(policyId, Mockito.mock(MongoReadJournal.class),
                                pubSubMediator, announcementManager, actorSystem);

                final TestProbe errorsProbe = TestProbe.apply(actorSystem);

                final Props parentProps = Props.create(Actor.class, () -> new AbstractActor() {

                    @Override
                    public void preStart() {
                        box.set(getContext().actorOf(persistentActorProps));
                    }

                    @Override
                    public SupervisorStrategy supervisorStrategy() {
                        return new OneForOneStrategy(true,
                                DeciderBuilder.matchAny(throwable -> {
                                    errorsProbe.ref().tell(throwable, getSelf());
                                    return SupervisorStrategy.restart();
                                }).build());
                    }

                    @Override
                    public Receive createReceive() {
                        return ReceiveBuilder.create()
                                .matchAny(message -> {
                                    if (getTestActor().equals(getSender())) {
                                        getContext().actorSelection(getSelf().path().child("*"))
                                                .forward(message, getContext());
                                    } else {
                                        getTestActor().forward(message, getContext());
                                    }
                                })
                                .build();
                    }
                });

                // WHEN: CheckForActivity is sent to a persistence actor of nonexistent policy after startup
                final ActorRef underTest = actorSystem.actorOf(parentProps);

                final Object checkForActivity = AbstractPersistenceActor.checkForActivity(1L);
                underTest.tell(checkForActivity, getRef());
                underTest.tell(checkForActivity, getRef());
                underTest.tell(checkForActivity, getRef());

                // THEN: persistence actor requests shutdown
                expectMsg(PolicySupervisorActor.Control.PASSIVATE);

                // THEN: persistence actor should not throw anything.
                errorsProbe.expectNoMessage(scala.concurrent.duration.Duration.create(3, TimeUnit.SECONDS));
            }
        };
    }

    @Test
    public void stayAliveIfAndOnlyIfFutureAnnouncementsExist() {
        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Instant expiryInstant = Instant.now().plus(Duration.ofHours(2));
                final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance(expiryInstant);
                final SubjectAnnouncement subjectAnnouncement =
                        SubjectAnnouncement.of(DittoDuration.parseDuration("1s"), false);
                final Subject subjectToAdd =
                        Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "anotherSubjectId"),
                                SubjectType.GENERATED, subjectExpiry, subjectAnnouncement);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeaders(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final PolicyId policyId = policy.getEntityId().orElseThrow();
                final ActorRef underTest = createPersistenceActorFor(this, policy);

                // GIVEN: a Policy is created without a Subject having an "expiry" date
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicyCommand, getRef());
                expectMsgClass(CreatePolicyResponse.class);

                // THEN: the persisted event should not have any tag
                Assertions.assertThat(expectPolicyEvent().getDittoHeaders().getJournalTags()).isEmpty();

                // WHEN: the Policy's subject is modified having an "expiry" in the near future
                final ModifySubject modifySubject =
                        ModifySubject.of(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth);
                underTest.tell(modifySubject, getRef());

                // THEN: the persisted event should have the "always-alive" tag
                expectMsgClass(ModifySubjectResponse.class);
                Assertions.assertThat(expectPolicyEvent().getDittoHeaders().getJournalTags())
                        .containsExactly(JOURNAL_TAG_ALWAYS_ALIVE);

                // WHEN: the subject is deleted
                final DeleteSubject deleteSubject =
                        DeleteSubject.of(policyId, POLICY_LABEL, subjectToAdd.getId(), headersMockWithOtherAuth);
                underTest.tell(deleteSubject, getRef());
                expectMsgClass(DeleteSubjectResponse.class);

                // THEN: a SubjectDeleted event without "always-alive" tag is emitted
                final var subjectDeleted = expectPolicyEvent();
                assertThat(subjectDeleted).isInstanceOf(SubjectDeleted.class);
                Assertions.assertThat(subjectDeleted.getDittoHeaders().getJournalTags()).isEmpty();

                // THEN: the persistent actor should stop itself after activity checks.
                final var checkForActivity = AbstractPersistenceActor.checkForActivity(Integer.MAX_VALUE);
                underTest.tell(checkForActivity, ActorRef.noSender());
                expectMsg(AbstractPersistenceSupervisor.Control.PASSIVATE);
            }
        };
    }

    private ActorRef createPersistenceActorFor(final TestKit testKit, final Policy policy) {
        return createPersistenceActorFor(testKit, policy.getEntityId().orElseThrow(NoSuchElementException::new));
    }

    private ActorRef createPersistenceActorFor(final TestKit testKit, final PolicyId policyId) {
        final var box = new AtomicReference<ActorRef>();
        final ActorRef announcementManager = createAnnouncementManager(policyId, box::get);
        final Props props = PolicyPersistenceActor.propsForTests(policyId, Mockito.mock(MongoReadJournal.class),
                pubSubMediator, announcementManager,
                actorSystem);
        final var persistenceActor = testKit.watch(testKit.childActorOf(props));
        box.set(persistenceActor);
        return persistenceActor;
    }

    private void waitPastTimeBorder() {
        final Instant now = Instant.now();
        final int secondRemainder = (int) (now.getEpochSecond() % 10L);
        final long nanoSecond = now.getNano();
        // sleep for some time if remainder is too close to the next border
        final int maxRemainder = 7;
        final long minNano = 100_000L;
        if (secondRemainder > maxRemainder || secondRemainder == 0 && nanoSecond < minNano) {
            final int effectiveRemainder = secondRemainder == 0 ? 10 : secondRemainder;
            final int secondUntilBorder = 10 - effectiveRemainder;
            final int millisToSleep = secondUntilBorder * 1000 + 500;
            try {
                LOGGER.info("Sleeping {} ms to avoid 10s border for subject expiry test", millisToSleep);
                TimeUnit.MILLISECONDS.sleep(millisToSleep);
            } catch (final Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    private PolicyEvent<?> expectPolicyEvent() {
        final var publish = (DistributedPubSubMediator.Publish) pubSubMediatorTestProbe.fishForMessage(
                scala.concurrent.duration.Duration.create(2, "s"),
                "publish event",
                publishedPolicyEvent()
        );
        assertThat(publish.message()).isInstanceOf(PolicyEvent.class);
        return (PolicyEvent<?>) publish.message();
    }

    private ActorRef createAnnouncementManager(final PolicyId policyId, final Supplier<ActorRef> box) {
        final var props = PolicyAnnouncementManager.props(policyId, policyAnnouncementPub, startForwarder(box),
                PolicyAnnouncementConfig.of(ConfigFactory.empty()));
        return actorSystem.actorOf(props);
    }

    private ActorRef startForwarder(final Supplier<ActorRef> box) {
        return actorSystem.actorOf(Props.create(ForwarderActor.class, () -> new ForwarderActor(box)));
    }

    private static Policy incrementRevision(final Policy policy, final int n) {
        Policy withIncrementedRevision = policy;
        for (int i = 0; i < n; i++) {
            withIncrementedRevision = withIncrementedRevision.toBuilder()
                    .setRevision(withIncrementedRevision.getRevision()
                            .map(Revision::increment)
                            .orElseGet(() -> PolicyRevision.newInstance(1)))
                    .build();
        }
        return withIncrementedRevision;
    }

    private static void terminate(final TestKit testKit, final ActorRef actor) {
        actor.tell(PoisonPill.getInstance(), ActorRef.noSender());
        testKit.expectTerminated(actor);
    }

    private static final class ForwarderActor extends AbstractActor {

        private final Supplier<ActorRef> box;

        private ForwarderActor(final Supplier<ActorRef> box) {
            this.box = box;
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .matchAny(message -> box.get().tell(message, getSender()))
                    .build();
        }
    }
}
