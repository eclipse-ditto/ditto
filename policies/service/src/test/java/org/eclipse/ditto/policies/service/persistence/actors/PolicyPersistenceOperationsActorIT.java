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
package org.eclipse.ditto.policies.service.persistence.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.purge.PurgeEntities;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespace;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoEventSourceITAssertions;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.operations.EntityPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.operations.NamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.PolicyPersistenceOperationsActor}.
 */
@AllValuesAreNonnullByDefault
public final class PolicyPersistenceOperationsActorIT extends MongoEventSourceITAssertions<PolicyId> {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private EntityPersistenceOperations entitiesOpsMock;
    private NamespacePersistenceOperations namespaceOpsMock;

    @Before
    public void setup() {
        entitiesOpsMock = Mockito.mock(EntityPersistenceOperations.class);
        namespaceOpsMock = Mockito.mock(NamespacePersistenceOperations.class);
    }

    @Test
    public void purgeNamespaceWithoutSuffix() {
        assertPurgeNamespace();
    }

    @Test
    public void purgeEntitiesWithNamespace() {
        assertPurgeEntitiesWithNamespace();
    }

    @Test
    public void shutdownWithoutTask() {
        final var actorSystem = actorSystemResource.getActorSystem();
        new TestKit(actorSystem) {{
            final var pubSubMediator = TestProbe.apply(actorSystem);
            final var underTest = startActorUnderTest(actorSystem, pubSubMediator.ref());
            pubSubMediator.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
            pubSubMediator.expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            underTest.tell(PolicyPersistenceOperationsActor.Control.SERVICE_UNBIND, getRef());
            final var unsub1 =
                    pubSubMediator.expectMsgClass(DistributedPubSubMediator.Unsubscribe.class);
            pubSubMediator.reply(new DistributedPubSubMediator.UnsubscribeAck(unsub1));
            final var unsub2 =
                    pubSubMediator.expectMsgClass(DistributedPubSubMediator.Unsubscribe.class);
            pubSubMediator.reply(new DistributedPubSubMediator.UnsubscribeAck(unsub2));
            expectMsg(Done.getInstance());

            underTest.tell(PolicyPersistenceOperationsActor.Control.SERVICE_REQUESTS_DONE, getRef());
            expectMsg(Done.getInstance());
        }};
    }

    @Test
    public void shutdownWithPurgeNamespaceTask() {
        final var actorSystem = actorSystemResource.getActorSystem();
        new TestKit(actorSystem) {{
            doAnswer(invocation -> Source.never()).when(namespaceOpsMock).purge(any());

            final var namespace = "namespaceToPurge";
            final var pubSubMediator = TestProbe.apply(actorSystem);
            final var underTest = startActorUnderTest(actorSystem, pubSubMediator.ref());
            pubSubMediator.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
            pubSubMediator.expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            final PurgeNamespace purgeNamespace = PurgeNamespace.of(namespace, DittoHeaders.empty());
            underTest.tell(purgeNamespace, getRef());
            underTest.tell(PolicyPersistenceOperationsActor.Control.SERVICE_REQUESTS_DONE, getRef());

            final DistributedPubSubMediator.Publish publish =
                    pubSubMediator.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.message()).isEqualTo(purgeNamespace);

            expectMsg(Done.getInstance());
        }};
    }

    @Test
    public void shutdownWithPurgeEntitiesTask() {
        final var actorSystem = actorSystemResource.getActorSystem();
        new TestKit(actorSystem) {{
            doAnswer(invocation -> Source.never()).when(entitiesOpsMock).purgeEntities(any());

            final var pubSubMediator = TestProbe.apply(actorSystem);
            final var underTest = startActorUnderTest(actorSystem, pubSubMediator.ref());
            pubSubMediator.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
            pubSubMediator.expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            final PurgeEntities purgeEntities = PurgeEntities.of(PolicyConstants.ENTITY_TYPE,
                    List.of(EntityId.of(PolicyConstants.ENTITY_TYPE, "org.eclipse.ditto:ditto1")),
                    DittoHeaders.empty());
            underTest.tell(purgeEntities, getRef());
            underTest.tell(PolicyPersistenceOperationsActor.Control.SERVICE_REQUESTS_DONE, getRef());

            final DistributedPubSubMediator.Publish publish =
                    pubSubMediator.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.message()).isInstanceOf(Shutdown.class);
            final DistributedPubSubMediator.Publish publish2 =
                    pubSubMediator.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish2.message()).isEqualTo(purgeEntities);

            expectMsg(Done.getInstance());
        }};
    }

    @Override
    protected String getServiceName() {
        // this loads the policies.conf as ActorSystem conf
        return "policies";
    }

    @Override
    protected String getResourceType() {
        return PolicyCommand.RESOURCE_TYPE;
    }

    @Override
    protected PolicyId toEntityId(final CharSequence entityId) {
        return PolicyId.of(entityId);
    }

    @Override
    protected Object getCreateEntityCommand(final PolicyId id) {
        final Policy policy = Policy.newBuilder(id)
                .forLabel("DUMMY")
                .setSubject("ditto:random-subject", SubjectType.GENERATED)
                .setResource(Resource.newInstance(PolicyCommand.RESOURCE_TYPE, "/",
                        EffectedPermissions.newInstance(Arrays.asList("READ", "WRITE"), Collections.emptyList())))
                .build();
        return CreatePolicy.of(policy, DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                .build());
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreatePolicyResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final PolicyId id) {
        return RetrievePolicy.of(id, DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                .build());
    }

    @Override
    protected Class<?> getRetrieveEntityResponseClass() {
        return RetrievePolicyResponse.class;
    }

    @Override
    protected Class<?> getEntityNotAccessibleClass() {
        return PolicyNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {

        final Props opsActorProps = PolicyPersistenceOperationsActor.props(pubSubMediator, mongoDbConfig, config,
                persistenceOperationsConfig);

        return actorSystem.actorOf(opsActorProps, PolicyPersistenceOperationsActor.ACTOR_NAME);
    }

    private ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator) {
        final Props opsActorProps = testProps(pubSubMediator);

        return actorSystem.actorOf(opsActorProps, PolicyPersistenceOperationsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final PolicyId id) {
        final Props props =
                PolicySupervisorActor.props(pubSubMediator, Mockito.mock(DistributedPub.class), null,
                        Mockito.mock(PolicyEnforcerProvider.class), Mockito.mock(MongoReadJournal.class));
        return system.actorOf(props, id.toString());
    }

    private Props testProps(final ActorRef pubSubMediator) {
        final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);

        return Props.create(PolicyPersistenceOperationsActor.class,
                () -> new PolicyPersistenceOperationsActor(pubSubMediator, namespaceOpsMock, entitiesOpsMock,
                        mongoClient, persistenceOperationsConfig));
    }

}
