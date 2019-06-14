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
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.NamespaceSuffixCollectionNames;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespace;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespaceResponse;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

/**
 * Tests subclasses of {@link AbstractEventSourceNamespaceOpsActor}.
 */
public abstract class EventSourceNamespaceOpsActorTestCases {

    private static final Duration EXPECT_MESSAGE_TIMEOUT = Duration.ofSeconds(10);
    private static final Random RANDOM = new Random();

    protected static MongoDbConfig mongoDbConfig;

    /**
     * Embedded MongoDB resource.
     */
    private static MongoDbResource mongoDbResource;
    protected static String mongoDbUri;

    private ActorSystem actorSystem;

    @Rule public TestName name = new TestName();

    @BeforeClass
    public static void startMongoDb() {
        mongoDbResource = new MongoDbResource("localhost");
        mongoDbResource.start();

        mongoDbUri = String.format("mongodb://%s:%s/test", mongoDbResource.getBindIp(), mongoDbResource.getPort());

        mongoDbConfig = DefaultMongoDbConfig.of(getConfig());
    }

    private static Config getConfig() {
        Config mongoDbTestConfig = ConfigFactory.parseMap(Collections.singletonMap("mongodb.uri", mongoDbUri));
        mongoDbTestConfig = mongoDbTestConfig.withFallback(ConfigFactory.parseResources("mongodb_test"));
        return mongoDbTestConfig;
    }

    @AfterClass
    public static void tearDown() {
        if (null != mongoDbResource) {
            mongoDbResource.stop();
            mongoDbResource = null;
        }
    }

    @After
    public void after() {
        stopActorSystem(actorSystem);
    }

    @Test
    public void purgeNamespaceWithSuffixBuilder() {
        // cannot start actor system in @Before because config is specific to the test executed
        final Config eventSourcingConfiguration = getEventSourcingConfiguration(ConfigFactory.empty());
        final Config ditto = ConfigFactory.empty().withValue("ditto", getConfig().root());
        actorSystem = startActorSystem(ditto.withFallback(eventSourcingConfiguration));
        // suffix builder is active by default
        purgeNamespace(actorSystem, eventSourcingConfiguration);
    }

    @Test
    public void purgeNamespaceWithoutSuffixBuilder() {
        final Config configOverride =
                ConfigFactory.parseString("akka.contrib.persistence.mongodb.mongo.suffix-builder.class=\"\"");
        final Config eventSourcingConfiguration = getEventSourcingConfiguration(configOverride);
        final Config ditto = ConfigFactory.empty().withValue("ditto", getConfig().root());
        actorSystem = startActorSystem(ditto.withFallback(eventSourcingConfiguration));
        // suffix builder is active by default
        purgeNamespace(actorSystem, eventSourcingConfiguration);
    }

    /**
     * Set up configuration required for event-sourcing to work.
     *
     * @param configOverride overriding config options.
     * @return config to feed the actor system and its actors.
     */
    private Config getEventSourcingConfiguration(final Config configOverride) {
        final String databaseName =
                name.getMethodName() + "-" + BigInteger.valueOf(System.currentTimeMillis()).toString(16);
        final String mongoUriValue = String.format("\"mongodb://%s:%s/%s\"\n",
                mongoDbResource.getBindIp(), mongoDbResource.getPort(), databaseName);

        // - do not log dead letters (i. e., events for which there is no subscriber)
        // - bind to random available port
        // - do not attempt to join an Akka cluster
        // - do not shutdown jvm on exit (breaks unit tests)
        // - make Mongo URI known to the persistence plugin and to the NamespaceOps actor
        final String testConfig = "akka.log-dead-letters=0\n" +
                "akka.remote.artery.bind.port=0\n" +
                "akka.cluster.seed-nodes=[]\n" +
                "akka.coordinated-shutdown.exit-jvm=off\n" +
                "ditto.things.log-incoming-messages=true\n" +
                "akka.contrib.persistence.mongodb.mongo.mongouri=\"" + mongoDbUri + "\"\n";

        // load the service config for info about event journal, snapshot store and metadata
        final Config configWithSuffixBuilder = ConfigFactory.parseString(testConfig)
                .withFallback(RawConfigSupplier.of(getServiceName()).get());

        // set namespace suffix config before persisting any event - NullPointerException otherwise
        NamespaceSuffixCollectionNames.setSupportedPrefixes(getSupportedPrefixes());

        return configOverride.withFallback(configWithSuffixBuilder).withFallback(getExtraConfig());
    }

    private ActorSystem startActorSystem(final Config config) {
        return ActorSystem.create("AkkaTestSystem-" + name.getMethodName(), config);
    }

    private static void stopActorSystem(@Nullable final ActorSystem actorSystem) {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    /**
     * @return name of the configured service.
     */
    protected abstract String getServiceName();

    /**
     * @return the extra Config to use in this test.
     */
    protected abstract Config getExtraConfig();

    /**
     * @return list of supported persistence ID prefixes - usually a singleton of the actor's resource type.
     */
    protected abstract List<String> getSupportedPrefixes();

    private void purgeNamespace(final ActorSystem actorSystem, final Config config) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(String.valueOf(UUID.randomUUID()))
                .build();

        new TestKit(actorSystem) {{
            final String purgedNamespace = "purgedNamespace.x" + RANDOM.nextInt(1000000);
            final String survivingNamespace = "survivingNamespace.x" + RANDOM.nextInt(1000000);

            final String purgedId = purgedNamespace + ":name";
            final String survivingId = survivingNamespace + ":name";

            final ActorRef pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
            final ActorRef actorToPurge = watch(startEntityActor(actorSystem, pubSubMediator, purgedId));
            final ActorRef survivingActor = watch(startEntityActor(actorSystem, pubSubMediator, survivingId));

            final ActorRef underTest = startActorUnderTest(actorSystem, pubSubMediator, config);

            // create 2 entities in 2 namespaces, 1 of which will be purged
            actorToPurge.tell(getCreateEntityCommand(purgedId), getRef());
            expectMsgClass(EXPECT_MESSAGE_TIMEOUT, getCreateEntityResponseClass());

            survivingActor.tell(getCreateEntityCommand(survivingId), getRef());
            expectMsgClass(EXPECT_MESSAGE_TIMEOUT, getCreateEntityResponseClass());

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
            expectMsgClass(EXPECT_MESSAGE_TIMEOUT, getRetrieveEntityResponseClass());

            // stop both actors - neither should pollute the database on death
            purgedActor.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(purgedActor);
            survivingActor.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(survivingActor);
        }};
    }

    /**
     * Start an entity's persistence actor.
     *
     * @param system the actor system.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param id ID of the entity.
     * @return reference to the entity actor.
     */
    protected abstract ActorRef startEntityActor(ActorSystem system, ActorRef pubSubMediator, String id);

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
    protected abstract Object getCreateEntityCommand(String id);

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
    protected abstract Object getRetrieveEntityCommand(String id);

    /**
     * @return resource type of the NamespaceOps actor being tested.
     */
    protected abstract String getResourceType();

}
