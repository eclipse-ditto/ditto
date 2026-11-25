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
package org.eclipse.ditto.things.service.persistence.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.base.model.exceptions.EnforcementTimeoutException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConflictException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.enforcement.CreatedPolicyRollback;
import org.eclipse.ditto.things.service.enforcement.TestSetup;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests how {@link ThingSupervisorActor} handles a policy it created for a {@code CreateThing} that then failed: a
 * persistence actor that stops without answering is answered once its ask times out (as on MongoDB) and the rollback
 * follows, the compensating deletion of the policy is retried and reported honestly, and only the policy of the failed
 * creation is ever deleted.
 */
public final class ThingSupervisorPolicyRollbackTest extends PersistenceActorTestBase {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Duration AT_ONCE = Duration.ofSeconds(3);
    private static final FiniteDuration PROBE_AT_ONCE = FiniteDuration.apply(3, TimeUnit.SECONDS);
    /**
     * Comfortably above the 2 s supervisor ask timeouts configured below: a persistence actor that stops without
     * answering is answered only when its ask times out.
     */
    private static final FiniteDuration PROBE_AFTER_ASK_TIMEOUT = FiniteDuration.apply(8, TimeUnit.SECONDS);

    private static final Config CUSTOM_CONFIG = ConfigFactory.parseString(
            // short ask timeouts keep the "persistence actor stops without answering" case fast
            "ditto.things.thing.supervisor.local-ask.timeout = 2s\n" +
            "ditto.things.thing.supervisor.local-ask.timeout-during-recovery = 2s\n" +
            "ditto.enforcement.created-policy-rollback {\n" +
            "  ask-timeout = 1s\n" +
            "  retry-strategy = BACKOFF_DELAY\n" +
            "  retry-attempts = 2\n" +
            "  backoff-delay { min = 100ms, max = 200ms, random-factor = 0.0 }\n" +
            "}\n");

    private DittoHeaders dittoHeaders;
    private Policy policy;
    private Logger rollbackLogger;
    private ListAppender<ILoggingEvent> rollbackLog;

    @Before
    public void setUpRollbackTest() {
        setup(CUSTOM_CONFIG);
        dittoHeaders = dittoHeadersV2.toBuilder().correlationId(UUID.randomUUID().toString()).build();
        policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy))));
        rollbackLogger = (Logger) LoggerFactory.getLogger(CreatedPolicyRollback.class);
        rollbackLog = new ListAppender<>();
        rollbackLog.start();
        rollbackLogger.addAppender(rollbackLog);
    }

    @After
    public void detachRollbackLog() {
        rollbackLogger.detachAppender(rollbackLog);
    }

    @Test
    public void thingPersistenceActorThatStopsOnTheCreateTriggersTheRollbackAfterTheAskTimeout() {
        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId().toBuilder().setPolicyId(null).build();
            final ActorRef underTest = startSupervisor(thing.getEntityId().orElseThrow(),
                    (thingId, readJournal, thingConfig, pub, searchShardRegionProxy, provider) ->
                            Props.create(StopsOnCreateThing.class));

            underTest.tell(CreateThing.of(thing, null, dittoHeaders), getRef());
            policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionTestProbe.reply(CreatePolicyResponse.of(POLICY_ID, policy, DittoHeaders.empty()));

            // the thing persistence actor stopped without answering: as on MongoDB, the supervisor waits for its ask
            // timeout, then rolls the created policy back and answers the timeout
            policiesShardRegionTestProbe.expectNoMessage(FiniteDuration.apply(1, TimeUnit.SECONDS));
            final DeletePolicy deletePolicy =
                    policiesShardRegionTestProbe.expectMsgClass(PROBE_AFTER_ASK_TIMEOUT, DeletePolicy.class);
            policiesShardRegionTestProbe.reply(DeletePolicyResponse.of(POLICY_ID, deletePolicy.getDittoHeaders()));
            expectMsgClass(AT_ONCE, EnforcementTimeoutException.class);
        }};
    }

    @Test
    public void failedCreateThingDoesNotDeleteThePolicyOfAnEarlierSuccessfulCreate() {
        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId().toBuilder().setPolicyId(null).build();
            final ThingId thingId = thing.getEntityId().orElseThrow();
            final TestProbe thingPersistenceActor = TestProbe.apply("thing-pa", actorSystem);
            final ActorRef underTest = startSupervisor(thingId, thingPersistenceActor.ref());

            // 1. a successful creation with an implicitly created policy
            underTest.tell(CreateThing.of(thing, null, dittoHeaders), getRef());
            policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionTestProbe.reply(CreatePolicyResponse.of(POLICY_ID, policy, DittoHeaders.empty()));
            thingPersistenceActor.expectMsgClass(CreateThing.class);
            final Thing created = thing.toBuilder().setPolicyId(POLICY_ID).build();
            thingPersistenceActor.reply(CreateThingResponse.of(created, dittoHeaders));
            expectMsgClass(AT_ONCE, CreateThingResponse.class);

            // 2. a later creation of the same thing that references this policy and is refused as a conflict
            final DittoHeaders secondHeaders =
                    dittoHeaders.toBuilder().correlationId(UUID.randomUUID().toString()).build();
            underTest.tell(CreateThing.of(created, null, secondHeaders), getRef());
            final RetrievePolicy retrievePolicy = policiesShardRegionTestProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionTestProbe.reply(
                    RetrievePolicyResponse.of(POLICY_ID, policy, retrievePolicy.getDittoHeaders()));
            thingPersistenceActor.expectMsgClass(CreateThing.class);
            thingPersistenceActor.reply(ThingConflictException.newBuilder(thingId).dittoHeaders(secondHeaders).build());

            // the policy belongs to the thing created in step 1: no rollback may delete it
            policiesShardRegionTestProbe.expectNoMessage();
            expectMsgClass(AT_ONCE, ThingConflictException.class);
        }};
    }

    @Test
    public void rollbackAnsweredUnavailableIsRetriedUntilThePolicyIsDeleted() {
        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId().toBuilder().setPolicyId(null).build();
            final ThingId thingId = thing.getEntityId().orElseThrow();
            final TestProbe thingPersistenceActor = TestProbe.apply("thing-pa", actorSystem);
            final ActorRef underTest = startSupervisor(thingId, thingPersistenceActor.ref());

            underTest.tell(CreateThing.of(thing, null, dittoHeaders), getRef());
            policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionTestProbe.reply(CreatePolicyResponse.of(POLICY_ID, policy, DittoHeaders.empty()));
            thingPersistenceActor.expectMsgClass(CreateThing.class);
            thingPersistenceActor.reply(
                    ThingUnavailableException.newBuilder(thingId).dittoHeaders(dittoHeaders).build());

            policiesShardRegionTestProbe.expectMsgClass(DeletePolicy.class);
            policiesShardRegionTestProbe.reply(
                    PolicyUnavailableException.newBuilder(POLICY_ID).dittoHeaders(dittoHeaders).build());
            final DeletePolicy retried = policiesShardRegionTestProbe.expectMsgClass(PROBE_AT_ONCE, DeletePolicy.class);
            policiesShardRegionTestProbe.reply(DeletePolicyResponse.of(POLICY_ID, retried.getDittoHeaders()));

            expectMsgClass(AT_ONCE, ThingUnavailableException.class); // the original error
            assertThat(rollbackLogLines()).containsExactly("INFO Policy <" + POLICY_ID +
                    "> deleted after rolling back the creation of Thing <" + thingId + "> (attempt 2).");
        }};
    }

    @Test
    public void rollbackThatStaysUnavailableGivesUpLogsAnErrorAndKeepsTheOriginalError() {
        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId().toBuilder().setPolicyId(null).build();
            final ThingId thingId = thing.getEntityId().orElseThrow();
            final TestProbe thingPersistenceActor = TestProbe.apply("thing-pa", actorSystem);
            final ActorRef underTest = startSupervisor(thingId, thingPersistenceActor.ref());

            underTest.tell(CreateThing.of(thing, null, dittoHeaders), getRef());
            policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionTestProbe.reply(CreatePolicyResponse.of(POLICY_ID, policy, DittoHeaders.empty()));
            thingPersistenceActor.expectMsgClass(CreateThing.class);
            thingPersistenceActor.reply(
                    ThingUnavailableException.newBuilder(thingId).dittoHeaders(dittoHeaders).build());

            for (int attempt = 1; attempt <= 3; attempt++) {
                policiesShardRegionTestProbe.expectMsgClass(PROBE_AT_ONCE, DeletePolicy.class);
                policiesShardRegionTestProbe.reply(
                        PolicyUnavailableException.newBuilder(POLICY_ID).dittoHeaders(dittoHeaders).build());
            }

            expectMsgClass(AT_ONCE, ThingUnavailableException.class); // the original error
            policiesShardRegionTestProbe.expectNoMessage();
            assertThat(rollbackLogLines()).singleElement().asString()
                    .startsWith("ERROR Rollback of the creation of Thing <" + thingId + "> failed: Policy <" +
                            POLICY_ID + "> could not be deleted in 3 attempt(s)");
        }};
    }

    /**
     * A thing persistence actor whose journal write fails: it receives the {@code CreateThing} and stops unanswered.
     */
    public static final class StopsOnCreateThing extends AbstractActor {

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(CreateThing.class, createThing -> getContext().stop(getSelf()))
                    .build();
        }
    }

    private ActorRef startSupervisor(final ThingId thingId,
            final ThingPersistenceActorPropsFactory persistenceActorPropsFactory) {
        final LiveSignalPub liveSignalPub = new TestSetup.DummyLiveSignalPub(pubSubMediator);
        final Props props = ThingSupervisorActor.props(pubSubMediator,
                thingsConfig,
                enforcementConfig,
                policiesShardRegion,
                distributedPub(),
                liveSignalPub,
                persistenceActorPropsFactory,
                null,
                policyEnforcerProvider,
                Mockito.mock(MongoReadJournal.class));
        return actorSystem.actorOf(props, thingId.toString());
    }

    private ActorRef startSupervisor(final ThingId thingId, final ActorRef persistenceActor) {
        final LiveSignalPub liveSignalPub = new TestSetup.DummyLiveSignalPub(pubSubMediator);
        final Props props = ThingSupervisorActor.props(pubSubMediator,
                thingsConfig,
                enforcementConfig,
                policiesShardRegion,
                distributedPub(),
                liveSignalPub,
                persistenceActor,
                null,
                policyEnforcerProvider,
                Mockito.mock(MongoReadJournal.class));
        return actorSystem.actorOf(props, thingId.toString());
    }

    private List<String> rollbackLogLines() {
        return List.copyOf(rollbackLog.list).stream()
                .map(event -> event.getLevel() + " " + event.getFormattedMessage())
                .toList();
    }

    private DistributedPub<ThingEvent<?>> distributedPub() {
        return new DistributedPub<>() {

            @Override
            public ActorRef getPublisher() {
                return pubSubMediator;
            }

            @Override
            public Object wrapForPublication(final ThingEvent<?> message, final CharSequence groupIndexKey) {
                return message;
            }

            @Override
            public <S extends ThingEvent<?>> Object wrapForPublicationWithAcks(final S message,
                    final CharSequence groupIndexKey, final AckExtractor<S> ackExtractor) {
                return wrapForPublication(message, groupIndexKey);
            }
        };
    }

}
