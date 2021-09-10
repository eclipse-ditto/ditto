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

import static org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.AbstractEventsourcedEvent;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyModified;
import org.eclipse.ditto.policies.service.persistence.serializer.DefaultPolicyMongoEventAdapter;
import org.eclipse.ditto.policies.service.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.eclipse.ditto.policies.service.persistence.testhelper.Assertions;
import org.eclipse.ditto.policies.service.persistence.testhelper.PoliciesJournalTestHelper;
import org.eclipse.ditto.policies.service.persistence.testhelper.PoliciesSnapshotTestHelper;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ExtendedActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for the snapshotting functionality of {@link org.eclipse.ditto.policies.service.persistence.actors.PolicyPersistenceActor}.
 */
public final class PolicyPersistenceActorSnapshottingTest extends PersistenceActorTestBase {

    private static final int PERSISTENCE_ASSERT_WAIT_AT_MOST_MS = 5_000;
    private static final long PERSISTENCE_ASSERT_RETRY_DELAY_MS = 500;
    private static final String POLICY_SNAPSHOT_PREFIX = "ditto.policies.policy.snapshot.";
    private static final String SNAPSHOT_INTERVAL = POLICY_SNAPSHOT_PREFIX + "interval";
    private static final String SNAPSHOT_THRESHOLD = POLICY_SNAPSHOT_PREFIX + "threshold";

    private DefaultPolicyMongoEventAdapter eventAdapter;
    private PoliciesJournalTestHelper<EventsourcedEvent<?>> journalTestHelper;
    private PoliciesSnapshotTestHelper<Policy> snapshotTestHelper;
    private Map<Class<? extends Command<?>>, BiFunction<Command<?>, Long, EventsourcedEvent<?>>>
            commandToEventMapperRegistry;
    private DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub = Mockito.mock(DistributedPub.class);

    @Override
    protected void setup(final Config customConfig) {
        super.setup(customConfig);
        eventAdapter = new DefaultPolicyMongoEventAdapter((ExtendedActorSystem) actorSystem);

        journalTestHelper = new PoliciesJournalTestHelper<>(actorSystem, this::convertJournalEntryToEvent,
                PolicyPersistenceActorSnapshottingTest::convertDomainIdToPersistenceId);
        snapshotTestHelper = new PoliciesSnapshotTestHelper<>(actorSystem,
                PolicyPersistenceActorSnapshottingTest::convertSnapshotDataToPolicy,
                PolicyPersistenceActorSnapshottingTest::convertDomainIdToPersistenceId);

        commandToEventMapperRegistry = new HashMap<>();
        commandToEventMapperRegistry.put(CreatePolicy.class, (command, revision) -> {
            final CreatePolicy createCommand = (CreatePolicy) command;
            return PolicyCreated.of(createCommand.getPolicy(), revision, TIMESTAMP, DittoHeaders.empty(), null);
        });
        commandToEventMapperRegistry.put(ModifyPolicy.class, (command, revision) -> {
            final ModifyPolicy modifyCommand = (ModifyPolicy) command;
            return PolicyModified.of(modifyCommand.getPolicy(), revision, TIMESTAMP, DittoHeaders.empty(), null);
        });
        commandToEventMapperRegistry.put(DeletePolicy.class, (command, revision) -> {
            final DeletePolicy deleteCommand = (DeletePolicy) command;
            return PolicyDeleted.of(deleteCommand.getEntityId(), revision, TIMESTAMP, DittoHeaders.empty(), null);
        });
    }

    private EventsourcedEvent<?> convertJournalEntryToEvent(final BsonDocument dbObject, final long sequenceNumber) {
        return ((AbstractEventsourcedEvent<?>) eventAdapter.fromJournal(dbObject, null).events().head())
                .setRevision(sequenceNumber);
    }

    private static String convertDomainIdToPersistenceId(final PolicyId domainId) {
        return PolicyPersistenceActor.PERSISTENCE_ID_PREFIX + domainId;
    }

    private static Policy convertSnapshotDataToPolicy(final BsonDocument dbObject, final long sequenceNumber) {
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final JsonObject json = dittoBsonJson.serialize(dbObject).asObject();

        final Policy policy = PoliciesModelFactory.newPolicy(json);

        assertThat(policy.getRevision().map(PolicyRevision::toLong).orElse(null)).isEqualTo(sequenceNumber);

        return policy;
    }

    /**
     * Check that a deleted policy is snapshot correctly and can be recreated.
     * Before the bug fix, the deleted policy was snapshot with incorrect data (previous version), thus it would be
     * handled as created after actor restart.
     */
    @Test
    public void deletedPolicyIsSnapshotWithCorrectDataAndCanBeRecreated() {
        setup(testConfig);

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final PolicyId policyId = policy.getEntityId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                final Policy policyCreated = createPolicyResponse.getPolicyCreated()
                        .orElseThrow(IllegalStateException::new);
                assertPolicyInResponse(policyCreated, policy, 1);

                final EventsourcedEvent<?> expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(policyId);

                final DeletePolicy deletePolicy = DeletePolicy.of(policyId, dittoHeadersV2);
                underTest.tell(deletePolicy, getRef());
                expectMsgEquals(DeletePolicyResponse.of(policyId, dittoHeadersV2));

                final Policy expectedDeletedSnapshot = PoliciesModelFactory.newPolicyBuilder()
                        .setRevision(2)
                        .setLifecycle(PolicyLifecycle.DELETED)
                        .build();
                assertSnapshots(policyId, Collections.singletonList(expectedDeletedSnapshot));
                final EventsourcedEvent<?> expectedDeletedEvent = toEvent(deletePolicy, 2);
                // created-event has been deleted due to snapshot
                assertJournal(policyId, Arrays.asList(expectedCreatedEvent, expectedDeletedEvent));

                // restart actor to recover policy state: make sure that the snapshot of deleted policy exists and can
                // be restored
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(policyId));

                final RetrievePolicy retrievePolicy = RetrievePolicy.of(policyId, dittoHeadersV2);
                underTest.tell(retrievePolicy, getRef());

                // A deleted Policy cannot be retrieved anymore.
                expectMsgClass(PolicyNotAccessibleException.class);

                // re-create the policy
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse reCreatePolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                assertPolicyInResponse(
                        reCreatePolicyResponse.getPolicyCreated().orElseThrow(NoSuchElementException::new), policy, 3);

                final EventsourcedEvent<?> expectedReCreatedEvent = toEvent(createPolicy, 3);
                assertJournal(policyId,
                        Arrays.asList(expectedCreatedEvent, expectedDeletedEvent, expectedReCreatedEvent));
                assertSnapshots(policyId, Collections.singletonList(expectedDeletedSnapshot));

                // retrieve the re-created policy
                underTest.tell(retrievePolicy, getRef());
                final RetrievePolicyResponse retrievePolicyAfterRestartResponse = expectMsgClass(RetrievePolicyResponse
                        .class);
                assertPolicyInResponse(retrievePolicyAfterRestartResponse.getPolicy(), policy, 3);
            }
        };
    }

    /**
     * Checks that the snapshots (in general) contain the expected revision no and data.
     * Before the bug fix, policies sometimes were snapshot with incorrect data (from previous version).
     */
    @Test
    public void policyInArbitraryStateIsSnapshotCorrectly() {
        setup(testConfig);

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final PolicyId policyId = policy.getEntityId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                assertPolicyInResponse(createPolicyResponse.getPolicyCreated().orElseThrow(IllegalStateException::new),
                        policy, 1);

                final EventsourcedEvent<?> expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(policyId);

                final Policy policyForModify = PoliciesModelFactory.newPolicyBuilder(policy)
                        .remove(ANOTHER_POLICY_ENTRY)
                        .build();
                final ModifyPolicy modifyPolicy = ModifyPolicy.of(policyId, policyForModify, dittoHeadersV2);
                underTest.tell(modifyPolicy, getRef());

                final ModifyPolicyResponse modifyPolicyResponse = expectMsgClass(ModifyPolicyResponse.class);
                assertThat(modifyPolicyResponse.getHttpStatus()).isEqualTo(HttpStatus.NO_CONTENT);

                final EventsourcedEvent<?> expectedModifiedEvent = toEvent(modifyPolicy, 2);
                // snapshot created
                assertJournal(policyId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent));
                assertSnapshots(policyId, Collections.singletonList(policyForModify));

                // Make sure that the actor has the correct revision no of 2
                final RetrievePolicy retrievePolicy = RetrievePolicy.of(policyId, dittoHeadersV2);
                underTest.tell(retrievePolicy, getRef());

                final RetrievePolicyResponse retrievePolicyResponse = expectMsgClass(RetrievePolicyResponse.class);
                assertPolicyInResponse(retrievePolicyResponse.getPolicy(), policyForModify, 2);

                // restart actor to recover policy state: make sure that the revision is still 2 (will be loaded from
                // snapshot)
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(policyId));

                underTest.tell(retrievePolicy, getRef());

                final RetrievePolicyResponse retrievePolicyAfterRestartResponse =
                        expectMsgClass(RetrievePolicyResponse.class);
                assertPolicyInResponse(retrievePolicyAfterRestartResponse.getPolicy(), policyForModify, 2);
            }
        };
    }

    /**
     * Checks that a snapshot is generated after the snapshot interval has passed, if there were changes to the
     * document.
     */
    @Test
    public void snapshotIsCreatedAfterSnapshotIntervalHasPassed() {
        final int snapshotIntervalSecs = 3;
        final Config customConfig = testConfig
                .withValue(SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(Long.MAX_VALUE))
                .withValue(SNAPSHOT_INTERVAL, ConfigValueFactory.fromAnyRef(Duration.ofSeconds(snapshotIntervalSecs)));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final PolicyId policyId = policy.getEntityId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                final Policy createdPolicy = createPolicyResponse.getPolicyCreated()
                        .orElseThrow(NoSuchElementException::new);
                assertPolicyInResponse(createdPolicy, policy, 1);

                final EventsourcedEvent<?> expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                // snapshots are empty, because the snapshot-interval has not yet passed
                assertSnapshotsEmpty(policyId);

                // wait until snapshot-interval has passed
                waitFor(snapshotIntervalSecs);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                // snapshot has been created
                assertSnapshots(policyId, Collections.singletonList(createdPolicy));

                final Policy policyForModify =
                        PoliciesModelFactory.newPolicyBuilder(policy).remove(ANOTHER_POLICY_ENTRY).build();
                final ModifyPolicy modifyPolicy = ModifyPolicy.of(policyId, policyForModify, dittoHeadersV2);
                underTest.tell(modifyPolicy, getRef());

                final ModifyPolicyResponse modifyPolicyResponse1 = expectMsgClass(ModifyPolicyResponse.class);
                assertThat(modifyPolicyResponse1.getHttpStatus()).isEqualTo(HttpStatus.NO_CONTENT);

                // wait again until snapshot-interval has passed
                waitFor(snapshotIntervalSecs);

                // snapshot has been created
                final EventsourcedEvent<?> expectedModifiedEvent1 = toEvent(modifyPolicy, 2);
                assertJournal(policyId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent1));
                assertSnapshots(policyId, Arrays.asList(createdPolicy, policyForModify));
            }
        };
    }

    @Test
    public void snapshotsAreNotCreatedTwiceIfSnapshotHasBeenAlreadyBeenCreatedDueToThresholdAndSnapshotIntervalHasPassed() {
        final int snapshotIntervalSecs = 3;
        final Config customConfig = testConfig.withValue(SNAPSHOT_INTERVAL,
                ConfigValueFactory.fromAnyRef(Duration.ofSeconds(snapshotIntervalSecs)));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final PolicyId policyId = policy.getEntityId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                final Policy createdPolicy = createPolicyResponse.getPolicyCreated()
                        .orElseThrow(NoSuchElementException::new);
                assertPolicyInResponse(createdPolicy, policy, 1);

                final EventsourcedEvent<?> expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(policyId);

                final Policy policyForModify = PoliciesModelFactory.newPolicyBuilder(policy).build();
                final ModifyPolicy modifyPolicy = ModifyPolicy.of(policyId, policyForModify, dittoHeadersV2);
                underTest.tell(modifyPolicy, getRef());

                final ModifyPolicyResponse modifyPolicyResponse1 = expectMsgClass(ModifyPolicyResponse.class);
                assertThat(modifyPolicyResponse1.getHttpStatus()).isEqualTo(HttpStatus.NO_CONTENT);

                final EventsourcedEvent<?> expectedModifiedEvent1 = toEvent(modifyPolicy, 2);
                assertJournal(policyId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent1));
                assertSnapshots(policyId, Collections.singletonList(policyForModify));

                // wait until snapshot-interval has passed
                waitFor(snapshotIntervalSecs);
                // there must have no snapshot been added
                assertSnapshots(policyId, Collections.singletonList(policyForModify));
            }
        };
    }

    private void assertSnapshotsEmpty(final PolicyId policyId) {
        assertSnapshots(policyId, Collections.emptyList());
    }

    private void assertJournal(final PolicyId policyId, final List<EventsourcedEvent<?>> expectedEvents) {
        retryOnAssertionError(() -> {
            final List<EventsourcedEvent<?>> actualEvents = journalTestHelper.getAllEvents(policyId);

            Assertions.assertListWithIndexInfo(actualEvents, (actual, expected) -> {
                assertThat(actual.getType()).isEqualTo(expected.getType());
                assertThat(actual.getRevision()).isEqualTo(expected.getRevision());

                if (actual instanceof PolicyModified) {
                    assertPolicyInJournal(((PolicyModified) actual).getPolicy(),
                            ((PolicyModified) expected).getPolicy());
                } else if (actual instanceof PolicyCreated) {
                    assertPolicyInJournal(((PolicyCreated) actual).getPolicy(), ((PolicyCreated) expected).getPolicy());
                } else if (actual instanceof PolicyDeleted) {
                    // no special check
                    assertTrue(true);
                } else {
                    throw new UnsupportedOperationException("No check for: " + actual.getClass());
                }
            }).isEqualTo(expectedEvents);
        });
    }

    private static void retryOnAssertionError(final Runnable r) {
        Assertions.retryOnAssertionError(r, PERSISTENCE_ASSERT_WAIT_AT_MOST_MS, PERSISTENCE_ASSERT_RETRY_DELAY_MS);
    }

    private static void assertPolicyInJournal(final Policy actualPolicy, final Policy expectedPolicy) {
        assertEqualJson(actualPolicy, expectedPolicy);
    }

    private ActorRef createPersistenceActorFor(final PolicyId policyId) {
        final SnapshotAdapter<Policy> snapshotAdapter = new PolicyMongoSnapshotAdapter();
        final Props props = PolicyPersistenceActor.propsForTests(policyId, snapshotAdapter, pubSubMediator,
                actorSystem.deadLetters());
        return actorSystem.actorOf(props);
    }

    private EventsourcedEvent<?> toEvent(final Command<?> command, final long revision) {
        final Class<? extends Command> clazz = command.getClass();
        final BiFunction<Command<?>, Long, EventsourcedEvent<?>> commandToEventFunction =
                commandToEventMapperRegistry.get(clazz);
        if (commandToEventFunction == null) {
            throw new UnsupportedOperationException("Mapping not yet implemented for type: " + clazz);
        }

        return commandToEventFunction.apply(command, revision);
    }

    private void assertSnapshots(final PolicyId policyId, final List<Policy> expectedSnapshots) {
        retryOnAssertionError(() -> {
            final List<Policy> snapshots = snapshotTestHelper.getAllSnapshotsAscending(policyId);
            Assertions.assertListWithIndexInfo(snapshots,
                    PolicyPersistenceActorSnapshottingTest::assertPolicyInSnapshot).isEqualTo(expectedSnapshots);
        });
    }

    private static void assertPolicyInSnapshot(final Policy actualPolicy, final Policy expectedPolicy) {
        assertPolicyInResponse(actualPolicy, expectedPolicy, expectedPolicy.getRevision().map(PolicyRevision::toLong)
                .orElseThrow(IllegalArgumentException::new));
    }

    private static void assertPolicyInResponse(final Policy actualPolicy, final Policy expectedPolicy,
            final long expectedRevision) {

        assertEqualJson(actualPolicy, PoliciesModelFactory.newPolicyBuilder(expectedPolicy)
                .setRevision(expectedRevision)
                .build());
    }

    private static void assertEqualJson(final Policy actualPolicy, final Policy expectedPolicy) {
        assertThat(actualPolicy.toJson()).isEqualTo(expectedPolicy.toJson());
    }

    private static void waitFor(final long timeout) {
        try {
            TimeUnit.SECONDS.sleep(timeout);
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

}
