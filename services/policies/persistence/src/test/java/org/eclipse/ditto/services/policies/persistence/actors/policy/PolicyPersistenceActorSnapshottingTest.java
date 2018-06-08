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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import static org.eclipse.ditto.model.policies.assertions.DittoPolicyAssertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.services.policies.persistence.actors.PersistenceActorTestBase;
import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoEventAdapter;
import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.eclipse.ditto.services.policies.persistence.testhelper.Assertions;
import org.eclipse.ditto.services.policies.persistence.testhelper.PoliciesJournalTestHelper;
import org.eclipse.ditto.services.policies.persistence.testhelper.PoliciesSnapshotTestHelper;
import org.eclipse.ditto.services.policies.util.ConfigKeys;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.services.utils.test.Retry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.junit.Test;

import com.mongodb.DBObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ExtendedActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for the snapshotting functionality of {@link PolicyPersistenceActor}.
 */
public final class PolicyPersistenceActorSnapshottingTest extends PersistenceActorTestBase {

    private static final int DEFAULT_TEST_SNAPSHOT_THRESHOLD = 2;
    private static final Duration VERY_LONG_DURATION = Duration.ofDays(100);
    private static final int PERSISTENCE_ASSERT_WAIT_AT_MOST_MS = 3000;
    private static final long PERSISTENCE_ASSERT_RETRY_DELAY_MS = 500;
    private PolicyMongoEventAdapter eventAdapter;
    private PoliciesJournalTestHelper<Event> journalTestHelper;
    private PoliciesSnapshotTestHelper<Policy> snapshotTestHelper;
    private Map<Class<? extends Command>, BiFunction<Command, Long, Event>> commandToEventMapperRegistry;

    private static Config createNewDefaultTestConfig() {
        return ConfigFactory.empty()
                .withValue(ConfigKeys.Policy.SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(
                        DEFAULT_TEST_SNAPSHOT_THRESHOLD))
                .withValue(ConfigKeys.Policy.ACTIVITY_CHECK_INTERVAL, ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION))
                .withValue(ConfigKeys.Policy.SNAPSHOT_INTERVAL, ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION));
    }

    private static void assertPolicyInSnapshot(final Policy actualPolicy, final Policy expectedPolicy) {
        assertPolicyInResponse(actualPolicy, expectedPolicy, expectedPolicy.getRevision().map(PolicyRevision::toLong)
                .orElseThrow(IllegalArgumentException::new));
    }

    protected static void assertPolicyInJournal(final Policy actualPolicy, Policy expectedPolicy) {
        final PolicyBuilder expectedPolicyBuilder = PoliciesModelFactory.newPolicyBuilder(expectedPolicy);
        expectedPolicy = expectedPolicyBuilder.build();

        assertEqualJson(actualPolicy, expectedPolicy);

        assertThat(actualPolicy.getModified()).isEmpty(); // is not required in journal entry
    }

    protected static void assertPolicyInResponse(final Policy actualPolicy, Policy expectedPolicy,
            final long expectedRevision) {
        final PolicyBuilder expectedPolicyBuilder = PoliciesModelFactory.newPolicyBuilder(expectedPolicy);
        expectedPolicyBuilder.setRevision(expectedRevision);
        expectedPolicy = expectedPolicyBuilder.build();

        assertEqualJson(actualPolicy, expectedPolicy);

        //assertThat(actualPolicy.getModified()).isPresent(); // we cannot check exact timestamp
    }

    private static void assertEqualJson(final Policy actualPolicy, final Policy expectedPolicy) {
        assertThat(actualPolicy.toJson()).isEqualTo(expectedPolicy.toJson());
    }

    private static Policy toDeletedPolicy(final Policy policy, final int newRevision) {
        return policy.toBuilder().setRevision(newRevision).setLifecycle(PolicyLifecycle.DELETED).build();
    }

    private static void retryOnAssertionError(final Runnable r) {
        Assertions.retryOnAssertionError(r, PERSISTENCE_ASSERT_WAIT_AT_MOST_MS, PERSISTENCE_ASSERT_RETRY_DELAY_MS);
    }

    private static void waitSecs(final long secs) {
        try {
            TimeUnit.SECONDS.sleep(secs);
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Policy convertSnapshotDataToPolicy(final DBObject dbObject, final long sequenceNumber) {
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final JsonObject json = dittoBsonJson.serialize(dbObject).asObject();

        final Policy policy = PoliciesModelFactory.newPolicy(json);

        assertThat(policy.getRevision().map(PolicyRevision::toLong).orElse(null)).isEqualTo(sequenceNumber);

        return policy;
    }

    private static String convertDomainIdToPersistenceId(final String domainId) {
        return PolicyPersistenceActor.PERSISTENCE_ID_PREFIX + domainId;
    }

    @Override
    protected void setup(final Config customConfig) {
        super.setup(customConfig);
        eventAdapter = new PolicyMongoEventAdapter((ExtendedActorSystem) actorSystem);

        journalTestHelper = new PoliciesJournalTestHelper<>(actorSystem, this::convertJournalEntryToEvent,
                PolicyPersistenceActorSnapshottingTest::convertDomainIdToPersistenceId);
        snapshotTestHelper = new PoliciesSnapshotTestHelper<>(actorSystem,
                PolicyPersistenceActorSnapshottingTest::convertSnapshotDataToPolicy,
                PolicyPersistenceActorSnapshottingTest::convertDomainIdToPersistenceId);


        commandToEventMapperRegistry = new HashMap<>();
        commandToEventMapperRegistry.put(CreatePolicy.class, (command, revision) -> {
            final CreatePolicy createCommand = (CreatePolicy) command;
            return PolicyCreated.of(createCommand.getPolicy(), revision, DittoHeaders.empty());
        });
        commandToEventMapperRegistry.put(ModifyPolicy.class, (command, revision) -> {
            final ModifyPolicy modifyCommand = (ModifyPolicy) command;
            return PolicyModified.of(modifyCommand.getPolicy(), revision, DittoHeaders.empty());
        });
        commandToEventMapperRegistry.put(DeletePolicy.class, (command, revision) -> {
            final DeletePolicy deleteCommand = (DeletePolicy) command;
            return PolicyDeleted.of(deleteCommand.getId(), revision, DittoHeaders.empty());
        });
    }

    /**
     * Check that a deleted policy is snapshotted correctly and can be recreated. Before the bugfix, the deleted policy
     * was snapshotted with incorrect data (previous version), thus it would be handled as created after actor restart.
     */
    @Test
    public void deletedPolicyIsSnapshottedWithCorrectDataAndCanBeRecreated() {
        setup(createNewDefaultTestConfig());

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final String policyId = policy.getId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                final Policy policyCreated = createPolicyResponse.getPolicyCreated()
                        .orElseThrow(IllegalStateException::new);
                assertPolicyInResponse(policyCreated, policy, 1);

                final Event expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(policyId);

                final DeletePolicy deletePolicy = DeletePolicy.of(policyId, dittoHeadersMockV2);
                underTest.tell(deletePolicy, getRef());
                expectMsgEquals(DeletePolicyResponse.of(policyId, dittoHeadersMockV2));

                final Policy expectedDeletedSnapshot = toDeletedPolicy(policyCreated, 2);
                assertSnapshots(policyId, Collections.singletonList(expectedDeletedSnapshot));
                final Event expectedDeletedEvent = toEvent(deletePolicy, 2);
                // created-event has been deleted due to snapshot
                assertJournal(policyId, Collections.singletonList(expectedDeletedEvent));

                // restart actor to recover policy state: make sure that the snapshot of deleted policy exists and can
                // be restored
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(policyId));

                final RetrievePolicy retrievePolicy = RetrievePolicy.of(policyId, dittoHeadersMockV2);
                underTest.tell(retrievePolicy, getRef());

                // A deleted Policy cannot be retrieved anymore.
                expectMsgClass(PolicyNotAccessibleException.class);

                // re-create the policy
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse reCreatePolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                assertPolicyInResponse(reCreatePolicyResponse.getPolicyCreated().orElse(null), policy, 3);

                final Event expectedReCreatedEvent = toEvent(createPolicy, 3);
                assertJournal(policyId, Arrays.asList(expectedDeletedEvent, expectedReCreatedEvent));
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
     * Checks that the snapshots (in general) contain the expected revision no and data. Before the bugfix, policys
     * sometimes were snapshotted with incorrect data (from previous version).
     */
    @Test
    public void policyInArbitraryStateIsSnapshottedCorrectly() {
        setup(createNewDefaultTestConfig());

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final String policyId = policy.getId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                assertPolicyInResponse(createPolicyResponse.getPolicyCreated().orElseThrow(IllegalStateException::new),
                        policy, 1);

                final Event expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(policyId);

                final Policy policyForModify = PoliciesModelFactory.newPolicyBuilder(policy).build();
                final ModifyPolicy modifyPolicy = ModifyPolicy.of(policyId, policyForModify, dittoHeadersMockV2);
                underTest.tell(modifyPolicy, getRef());

                final ModifyPolicyResponse modifyPolicyResponse = expectMsgClass(ModifyPolicyResponse.class);
                assertThat(modifyPolicyResponse.getStatusCode()).isEqualTo(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent = toEvent(modifyPolicy, 2);
                // created-event has been deleted due to snapshot
                assertJournal(policyId, Collections.singletonList(expectedModifiedEvent));
                assertSnapshots(policyId, Collections.singletonList(policyForModify));

                // Make sure that the actor has the correct revision no of 2
                final RetrievePolicy retrievePolicy = RetrievePolicy.of(policyId, dittoHeadersMockV2);
                underTest.tell(retrievePolicy, getRef());

                final RetrievePolicyResponse retrievePolicyResponse = expectMsgClass(RetrievePolicyResponse
                        .class);
                assertPolicyInResponse(retrievePolicyResponse.getPolicy(), policy, 2);

                // restart actor to recover policy state: make sure that the revision is still 2 (will be loaded from
                // snapshot)
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(policyId));

                underTest.tell(retrievePolicy, getRef());

                final RetrievePolicyResponse retrievePolicyAfterRestartResponse = expectMsgClass(RetrievePolicyResponse
                        .class);
                assertPolicyInResponse(retrievePolicyAfterRestartResponse.getPolicy(), policy, 2);
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
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Policy.SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(Long.MAX_VALUE)).
                withValue(ConfigKeys.Policy.SNAPSHOT_INTERVAL,
                        ConfigValueFactory.fromAnyRef(Duration.ofSeconds(snapshotIntervalSecs)));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final String policyId = policy.getId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                final Policy createdPolicy = createPolicyResponse.getPolicyCreated().orElse(null);
                assertPolicyInResponse(createdPolicy, policy, 1);

                final Event expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                // snapshots are empty, because the snapshot-interval has not yet passed
                assertSnapshotsEmpty(policyId);

                // wait until snapshot-interval has passed
                waitSecs(snapshotIntervalSecs);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                // snapshot has been created
                assertSnapshots(policyId, Collections.singletonList(createdPolicy));

                final Policy policyForModify = PoliciesModelFactory.newPolicyBuilder(policy).build();
                final ModifyPolicy modifyPolicy = ModifyPolicy.of(policyId, policyForModify, dittoHeadersMockV2);
                underTest.tell(modifyPolicy, getRef());

                final ModifyPolicyResponse modifyPolicyResponse1 = expectMsgClass(ModifyPolicyResponse.class);
                assertThat(modifyPolicyResponse1.getStatusCode()).isEqualTo(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent1 = toEvent(modifyPolicy, 2);
                assertJournal(policyId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent1));
                // snapshot has not yet been made, because the snapshot-interval has not yet passed
                assertSnapshots(policyId, Collections.singletonList(createdPolicy));

                // wait again until snapshot-interval has passed
                waitSecs(snapshotIntervalSecs);
                // because snapshot has been created, the "old" created-event has been deleted
                assertJournal(policyId, Collections.singletonList(expectedModifiedEvent1));
                // snapshot has been created and old snapshot has been deleted
                assertSnapshots(policyId, Collections.singletonList(createdPolicy));
            }
        };
    }

    /**
     *
     */
    @Test
    public void snapshotsAreNotCreatedTwiceIfSnapshotHasBeenAlreadyBeenCreatedDueToThresholdAndSnapshotIntervalHasPassed() {
        final int snapshotIntervalSecs = 3;
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Policy.SNAPSHOT_INTERVAL,
                        ConfigValueFactory.fromAnyRef(Duration.ofSeconds(snapshotIntervalSecs)));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final String policyId = policy.getId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(policyId);

                final CreatePolicy createPolicy = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicy, getRef());

                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                final Policy createdPolicy = createPolicyResponse.getPolicyCreated().orElse(null);
                assertPolicyInResponse(createdPolicy, policy, 1);

                final Event expectedCreatedEvent = toEvent(createPolicy, 1);
                assertJournal(policyId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(policyId);

                final Policy policyForModify = PoliciesModelFactory.newPolicyBuilder(policy).build();
                final ModifyPolicy modifyPolicy = ModifyPolicy.of(policyId, policyForModify, dittoHeadersMockV2);
                underTest.tell(modifyPolicy, getRef());

                final ModifyPolicyResponse modifyPolicyRsponse1 = expectMsgClass(ModifyPolicyResponse.class);
                assertThat(modifyPolicyRsponse1.getStatusCode()).isEqualTo(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent1 = toEvent(modifyPolicy, 2);
                assertJournal(policyId, Collections.singletonList(expectedModifiedEvent1));
                assertSnapshots(policyId, Collections.singletonList(policyForModify));

                // wait until snapshot-interval has passed
                waitSecs(snapshotIntervalSecs);
                // there must have no snapshot been added
                assertSnapshots(policyId, Collections.singletonList(policyForModify));
            }
        };
    }

    /**
     *
     */
    @Test
    public void actorCannotBeStartedWithNegativeSnapshotThreshold() {
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Policy.SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(-1));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor("fail");
                watch(underTest);
                expectTerminated(underTest);
            }
        };
    }

    private void assertSnapshotsEmpty(final String policyId) {
        assertSnapshots(policyId, Collections.emptyList());
    }

    private void assertJournal(final String policyId, final List<Event> expectedEvents) {
        retryOnAssertionError(() -> {
            final List<Event> actualEvents = journalTestHelper.getAllEvents(policyId);

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

    protected ActorRef createPersistenceActorFor(final String policyId) {
        final PolicyMongoSnapshotAdapter snapshotAdapter = new PolicyMongoSnapshotAdapter();
        final Props props = PolicyPersistenceActor.props(policyId, snapshotAdapter, pubSubMediator);
        return actorSystem.actorOf(props);
    }

    private Event toEvent(final Command command, final long revision) {
        final Class<? extends Command> clazz = command.getClass();
        final BiFunction<Command, Long, Event> commandToEventFunction = commandToEventMapperRegistry.get(clazz);
        if (commandToEventFunction == null) {
            throw new UnsupportedOperationException("Mapping not yet implemented for type: " + clazz);
        }

        return commandToEventFunction.apply(command, revision);
    }

    private void assertSnapshots(final String policyId, final List<Policy> expectedSnapshots) {
        retryOnAssertionError(() -> {
            final List<Policy> snapshots = snapshotTestHelper.getAllSnapshotsAscending(policyId);
            Assertions.assertListWithIndexInfo(snapshots,
                    PolicyPersistenceActorSnapshottingTest::assertPolicyInSnapshot).isEqualTo(expectedSnapshots);
        });
    }

    private Event convertJournalEntryToEvent(final DBObject dbObject, final long sequenceNumber) {
        return ((Event) eventAdapter.fromJournal(dbObject, null).events().head()).setRevision(sequenceNumber);
    }

}
