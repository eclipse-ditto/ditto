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
package org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource;

import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.PersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.base.api.common.purge.PurgeEntities;
import org.eclipse.ditto.base.api.common.purge.PurgeEntitiesResponse;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespace;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespaceResponse;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

/**
 * Tests subclasses of {@link org.eclipse.ditto.internal.utils.persistence.operations.AbstractPersistenceOperationsActor} which provide purging by namespace on a event source
 * persistence.
 */
public abstract class MongoEventSourceITAssertions<I extends EntityId> {

    private static final Duration EXPECT_MESSAGE_TIMEOUT = Duration.ofSeconds(30);
    private static final Random RANDOM = new Random();

    protected static MongoDbConfig mongoDbConfig;

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();
    protected static String mongoDbUri;
    protected static PersistenceOperationsConfig persistenceOperationsConfig;

    private ActorSystem actorSystem;

    @BeforeClass
    public static void startMongoDb() {
        mongoDbUri = String.format("mongodb://%s:%s/test", MONGO_RESOURCE.getBindIp(), MONGO_RESOURCE.getPort());

        mongoDbConfig = DefaultMongoDbConfig.of(getConfig());
        persistenceOperationsConfig = mock(PersistenceOperationsConfig.class);
        Mockito.when(persistenceOperationsConfig.getDelayAfterPersistenceActorShutdown())
                .thenReturn(Duration.ofSeconds(5L));
    }

    private static Config getConfig() {
        Config mongoDbTestConfig = ConfigFactory.parseMap(Collections.singletonMap("mongodb.uri", mongoDbUri));
        mongoDbTestConfig = mongoDbTestConfig.withFallback(ConfigFactory.parseResources("mongodb_test"));
        return mongoDbTestConfig;
    }

    @After
    public void shutDownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    protected void assertPurgeNamespace() {
        purgeNamespace(getEventSourcingConfiguration());
    }

    protected void assertPurgeEntitiesWithoutNamespace() {
        purgeEntities(getEventSourcingConfiguration(), false);
    }

    protected void assertPurgeEntitiesWithNamespace() {
        purgeEntities(getEventSourcingConfiguration(), true);
    }

    /**
     * @return name of the configured service.
     */
    protected abstract String getServiceName();

    /**
     * @return list of supported persistence ID prefixes - usually a singleton of the actor's resource type.
     */
    protected final List<String> getSupportedPrefixes() {
        return Collections.singletonList(getResourceType());
    }

    /**
     * Start an entity's persistence actor.
     *
     * @param system the actor system.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param id ID of the entity.
     * @return reference to the entity actor.
     */
    protected abstract ActorRef startEntityActor(ActorSystem system, ActorRef pubSubMediator, I id);

    /**
     * Starts the NamespaceOps actor.
     *
     * @param actorSystem the actor system.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param config configuration with info about event journal, snapshot store, metadata and database.
     * @return reference of the NamespaceOps actor.
     */
    protected abstract ActorRef startActorUnderTest(ActorSystem actorSystem, ActorRef pubSubMediator, Config config);

    /**
     * Get the command to create an entity belonging to the given resource type.
     *
     * @param id ID of the entity.
     * @return Command to create it.
     */
    protected abstract Object getCreateEntityCommand(I id);

    /**
     * @return type of responses for successful entity creation.
     */
    protected abstract Class<?> getCreateEntityResponseClass();

    /**
     * @return type of responses for retrieval of nonexistent entities.
     */
    protected abstract Class<?> getEntityNotAccessibleClass();

    /**
     * @return type of responses for successful entity retrieval.
     */
    protected abstract Class<?> getRetrieveEntityResponseClass();

    /**
     * Get the command to retrieve an entity belonging to the given resource type.
     *
     * @param id ID of the entity.
     * @return Command to retrieve it.
     */
    protected abstract Object getRetrieveEntityCommand(final I id);

    /**
     * @return resource type of the NamespaceOps actor being tested.
     */
    protected abstract String getResourceType();

    /**
     * Set up configuration required for event-sourcing to work.
     *
     * @return config to feed the actor system and its actors.
     */
    private Config getEventSourcingConfiguration() {
        // - do not log dead letters (i. e., events for which there is no subscriber)
        // - bind to random available port
        // - do not attempt to join an Akka cluster
        // - do not shutdown jvm on exit (breaks unit tests)
        // - make Mongo URI known to the persistence plugin and to the NamespaceOps actor
        final String testConfig = "akka.log-dead-letters=0\n" +
                "akka.persistence.journal-plugin-fallback.circuit-breaker.call-timeout=30s\n" +
                "akka-contrib-mongodb-persistence-policies-journal.circuit-breaker.call-timeout=30s\n" +
                "akka-contrib-mongodb-persistence-things-journal.circuit-breaker.call-timeout=30s\n" +
                "akka.remote.artery.bind.port=0\n" +
                "akka.cluster.seed-nodes=[]\n" +
                "akka.coordinated-shutdown.exit-jvm=off\n" +
                "ditto.things.log-incoming-messages=true\n" +
                "akka.contrib.persistence.mongodb.mongo.mongouri=\"" + mongoDbUri + "\"\n";

        // load the service config for info about event journal, snapshot store and metadata
        return ConfigFactory.parseString(testConfig).withFallback(RawConfigSupplier.of(getServiceName()).get());
    }

    private void purgeNamespace(final Config config) {
        actorSystem = startActorSystem(config);
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(String.valueOf(UUID.randomUUID()))
                .build();

        new TestKit(actorSystem) {{
            final String purgedNamespace = "purgedNamespace.x" + RANDOM.nextInt(1_000_000);
            final String survivingNamespace = "survivingNamespace.x" + RANDOM.nextInt(1_000_000);

            final I purgedId = toEntityId(purgedNamespace + ":name");
            final I survivingId = toEntityId(survivingNamespace + ":name");

            final ActorRef pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
            final ActorRef actorToPurge = watch(startEntityActor(actorSystem, pubSubMediator, purgedId));
            final ActorRef survivingActor = watch(startEntityActor(actorSystem, pubSubMediator, survivingId));

            final ActorRef underTest = startActorUnderTest(actorSystem, pubSubMediator, config);

            // create 2 entities in 2 namespaces, 1 of which will be purged
            actorToPurge.tell(getCreateEntityCommand(purgedId), getRef());
            expectCreateEntityResponse(this);

            survivingActor.tell(getCreateEntityCommand(survivingId), getRef());
            expectCreateEntityResponse(this);

            // kill the actor in the namespace to be purged to avoid write conflict
            actorToPurge.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(EXPECT_MESSAGE_TIMEOUT, actorToPurge);

            // purge the namespace
            underTest.tell(PurgeNamespace.of(purgedNamespace, dittoHeaders), getRef());
            expectMsg(EXPECT_MESSAGE_TIMEOUT,
                    PurgeNamespaceResponse.successful(purgedNamespace, getResourceType(), dittoHeaders));

            // restart the actor in the purged namespace - it should work as if its entity never existed
            final ActorRef purgedActor = watch(startEntityActor(actorSystem, pubSubMediator, purgedId));
            purgedActor.tell(getRetrieveEntityCommand(purgedId), getRef());
            expectMsgClass(EXPECT_MESSAGE_TIMEOUT, getEntityNotAccessibleClass());

            // the actor outside the purged namespace should not be affected
            survivingActor.tell(getRetrieveEntityCommand(survivingId), getRef());
            expectMsgClass(getRetrieveEntityResponseClass());
        }};
    }

    private void purgeEntities(final Config config, final boolean prependNamespace) {
        actorSystem = startActorSystem(config);
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(String.valueOf(UUID.randomUUID()))
                .build();

        new TestKit(actorSystem) {{
            final Random random = new Random();

            final String namespace = "purgedNamespace.x" + random.nextInt(1000000);

            final I purgedId1 = toEntityId(prependNamespace("purgedId1", namespace, prependNamespace));
            final I purgedId2 = toEntityId(prependNamespace("purgedId2", namespace, prependNamespace));
            final I survivingId = toEntityId(prependNamespace("survingId", namespace, prependNamespace));

            final ActorRef pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
            final ActorRef actorToPurge1 = watch(startEntityActor(actorSystem, pubSubMediator, purgedId1));
            final ActorRef actorToPurge2 = watch(startEntityActor(actorSystem, pubSubMediator, purgedId2));
            final ActorRef survivingActor = watch(startEntityActor(actorSystem, pubSubMediator, survivingId));

            final ActorRef underTest = startActorUnderTest(actorSystem, pubSubMediator, config);

            // create 2 entities which will be purged
            actorToPurge1.tell(getCreateEntityCommand(purgedId1), getRef());
            expectCreateEntityResponse(this);

            actorToPurge2.tell(getCreateEntityCommand(purgedId2), getRef());
            expectCreateEntityResponse(this);

            // create one entity which won't be purged
            survivingActor.tell(getCreateEntityCommand(survivingId), getRef());
            expectCreateEntityResponse(this);

            // kill the actors for the entities to be purged to avoid write conflict
            actorToPurge1.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(actorToPurge1);

            actorToPurge2.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(actorToPurge2);

            // purge the 2 entities
            final EntityType entityType = EntityType.of(getResourceType());
            final PurgeEntities purgeEntities =
                    PurgeEntities.of(entityType, Arrays.asList(purgedId1, purgedId2), dittoHeaders);
            underTest.tell(purgeEntities, getRef());
            expectMsg(Duration.ofSeconds(8), PurgeEntitiesResponse.successful(entityType, dittoHeaders));

            sleep(Duration.ofSeconds(5L));

            // restart the actors for the purged entities - they should work as if its entity never existed
            final ActorRef purgedActor1 = watch(startEntityActor(actorSystem, pubSubMediator, purgedId1));
            purgedActor1.tell(getRetrieveEntityCommand(purgedId1), getRef());
            expectMsgClass(Duration.ofSeconds(10L), getEntityNotAccessibleClass());

            final ActorRef purgedActor2 = watch(startEntityActor(actorSystem, pubSubMediator, purgedId2));
            purgedActor2.tell(getRetrieveEntityCommand(purgedId2), getRef());
            expectMsgClass(getEntityNotAccessibleClass());

            // the actor outside the purged namespace should not be affected
            survivingActor.tell(getRetrieveEntityCommand(survivingId), getRef());
            expectMsgClass(getRetrieveEntityResponseClass());
        }};
    }

    protected abstract I toEntityId(final CharSequence entityId);

    private void expectCreateEntityResponse(final TestKit testKit) {
        testKit.expectMsgClass(EXPECT_MESSAGE_TIMEOUT, getCreateEntityResponseClass());
    }

    private ActorSystem startActorSystem(final Config config) {
        final String name = getClass().getSimpleName() + '-' + UUID.randomUUID().toString();
        return ActorSystem.create(name, config);
    }

    private static String prependNamespace(final String id, final String ns, final boolean prepend) {
        if (prepend) {
            return ns + ':' + id;
        } else {
            return id;
        }
    }

    private static void sleep(final Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (final Exception e) {
            throw new CompletionException(e);
        }
    }

}
