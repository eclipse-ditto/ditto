package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.List;
import java.util.Random;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.NamespaceSuffixCollectionNames;
import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.SuffixBuilderConfig;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.signals.commands.devops.namespace.PurgeNamespace;
import org.eclipse.ditto.signals.commands.devops.namespace.PurgeNamespaceResponse;
import org.eclipse.ditto.signals.commands.devops.namespace.QueryNamespaceEmptiness;
import org.eclipse.ditto.signals.commands.devops.namespace.QueryNamespaceEmptinessResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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

    /**
     * Embedded MongoDB resource.
     */
    protected static MongoDbResource mongoResource;

    /**
     * @return name of the configured service.
     */
    protected abstract String serviceName();

    /**
     * @return resource type of the NamespaceOps actor being tested.
     */
    protected abstract String resourceType();

    /**
     * @return list of supported persistence ID prefixes - usually a singleton of the actor's resource type.
     */
    protected abstract List<String> supportedPrefixes();

    /**
     * Get the command to create an entity belonging to the given resource type.
     *
     * @param id ID of the entity.
     * @return Command to create it.
     */
    protected abstract Object createEntity(final String id);

    /**
     * @return Type of responses for successful entity creation.
     */
    protected abstract Class<?> createEntityResponseClass();

    /**
     * Get the command to retrieve an entity belonging to the given resource type.
     *
     * @param id ID of the entity.
     * @return Command to retrieve it.
     */
    protected abstract Object retrieveEntity(final String id);

    /**
     * @return Type of responses for successful entity retrieval.
     */
    protected abstract Class<?> retrieveEntityResponseClass();

    /**
     * @return Type of responses for retrieval of nonexistent entities.
     */
    protected abstract Class<?> entityNotAccessibleClass();

    /**
     * Start the NamespaceOps actor.
     *
     * @param actorSystem the actor system.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param config Configuration with info about event journal, snapshot store, metadata and database.
     * @return reference of the NamespaceOps actor.
     */
    protected abstract ActorRef startActorUnderTest(final ActorSystem actorSystem,
            final ActorRef pubSubMediator,
            final Config config);

    /**
     * Start an entity's persistence actor.
     *
     * @param system the actor system.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param id ID of the entity.
     * @return reference to the entity actor.
     */
    protected abstract ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator,
            final String id);

    @BeforeClass
    public static void startMongo() {
        mongoResource = new MongoDbResource("localhost");
        mongoResource.start();
    }

    @AfterClass
    public static void teardown() {
        if (mongoResource != null) {
            mongoResource.stop();
            mongoResource = null;
        }
    }

    /**
     * Set up configuration required for event-sourcing to work.
     *
     * @param configOverride overriding config options.
     * @return config to feed the actor system and its actors.
     */
    private Config setup(final Config configOverride) {
        final String databaseName = "test";
        final String mongoUriValue = String.format("\"mongodb://%s:%s/%s\"\n",
                mongoResource.getBindIp(), mongoResource.getPort(), databaseName);

        // - do not log dead letters (i. e., events for which there is no subscriber)
        // - bind to random available port
        // - do not attempt to join an Akka cluster
        // - make Mongo URI known to the persistence plugin and to the NamespaceOps actor
        final String testConfig = "akka.log-dead-letters=0\n" +
                "akka.remote.netty.tcp.port=0\n" +
                "akka.cluster.seed-nodes=[]\n" +
                "akka.contrib.persistence.mongodb.mongo.mongouri=" + mongoUriValue +
                "ditto.services-utils-config.mongodb.uri=" + mongoUriValue;

        // load the service config for info about event journal, snapshot store and metadata
        final Config configWithSuffixBuilder = ConfigFactory.parseString(testConfig)
                .withFallback(ConfigUtil.determineConfig(serviceName()));

        // set namespace suffix config before persisting any event - NullPointerException otherwise
        NamespaceSuffixCollectionNames.setConfig(new SuffixBuilderConfig(supportedPrefixes()));

        return configOverride.withFallback(configWithSuffixBuilder);
    }

    @Test
    public void purgeNamespaceWithSuffixBuilder() {
        // suffix builder is active by default
        purgeNamespace(setup(ConfigFactory.empty()));
    }

    @Test
    public void purgeNamespaceWithoutSuffixBuilder() {
        final Config configOverride =
                ConfigFactory.parseString("akka.contrib.persistence.mongodb.mongo.suffix-builder.class=\"\"");
        purgeNamespace(setup(configOverride));
    }

    private void purgeNamespace(final Config config) {
        final ActorSystem actorSystem = ActorSystem.create(getClass().getSimpleName(), config);
        new TestKit(actorSystem) {{
            final Random random = new Random();
            final String purgedNamespace = "purgedNamespace.x" + random.nextInt(1000000);
            final String survivingNamespace = "survivingNamespace.x" + random.nextInt(1000000);

            final String purgedId = purgedNamespace + ":name";
            final String survivingId = survivingNamespace + ":name";

            final ActorRef pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
            final ActorRef actorToPurge = watch(startEntityActor(actorSystem, pubSubMediator, purgedId));
            final ActorRef survivingActor = watch(startEntityActor(actorSystem, pubSubMediator, survivingId));

            final ActorRef underTest = startActorUnderTest(actorSystem, pubSubMediator, config);

            // create 2 entities in 2 namespaces, 1 of which will be purged
            actorToPurge.tell(createEntity(purgedId), getRef());
            expectMsgClass(createEntityResponseClass());

            survivingActor.tell(createEntity(survivingId), getRef());
            expectMsgClass(createEntityResponseClass());

            // neither namespace should be empty
            underTest.tell(QueryNamespaceEmptiness.of(purgedNamespace), getRef());
            expectMsg(queryResponse(purgedNamespace, false));
            underTest.tell(QueryNamespaceEmptiness.of(survivingNamespace), getRef());
            expectMsg(queryResponse(survivingNamespace, false));

            // kill the actor in the namespace to be purged to avoid write conflict
            actorToPurge.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(actorToPurge);

            // purge the namespace
            underTest.tell(PurgeNamespace.of(purgedNamespace), getRef());
            expectMsg(purgeResponse(purgedNamespace, true));

            // restart the actor in the purged namespace - it should work as if its entity never existed
            final ActorRef purgedActor = watch(startEntityActor(actorSystem, pubSubMediator, purgedId));
            purgedActor.tell(retrieveEntity(purgedId), getRef());
            expectMsgClass(entityNotAccessibleClass());

            // the actor outside the purged namespace should not be affected
            survivingActor.tell(retrieveEntity(survivingId), getRef());
            expectMsgClass(retrieveEntityResponseClass());

            // stop both actors - neither should pollute the database on death
            purgedActor.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(purgedActor);
            survivingActor.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(survivingActor);

            // the purged namespace should stay empty and the other namespace should stay non-emnpty
            underTest.tell(QueryNamespaceEmptiness.of(purgedNamespace), getRef());
            expectMsg(queryResponse(purgedNamespace, true));
            underTest.tell(QueryNamespaceEmptiness.of(survivingNamespace), getRef());
            expectMsg(queryResponse(survivingNamespace, false));
        }};
    }

    private PurgeNamespaceResponse purgeResponse(final String namespace, final boolean isSuccessful) {
        return PurgeNamespaceResponse.newBuilder()
                .resourceType(resourceType())
                .namespace(namespace)
                .setSuccessful(isSuccessful)
                .build();
    }

    private QueryNamespaceEmptinessResponse queryResponse(final String namespace, final boolean isEmpty) {
        return QueryNamespaceEmptinessResponse.newBuilder()
                .resourceType(resourceType())
                .namespace(namespace)
                .setEmptiness(isEmpty)
                .build();
    }
}
