/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo;

import static com.mongodb.client.model.Filters.eq;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.bson.Document;
import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatusResponse;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;

import com.mongodb.ReadPreference;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor for handling calls to the mongodb.
 */
public final class MongoHealthChecker extends AbstractHealthCheckingActor {

    private static final String TEST_COLLECTION_NAME = "test";
    private static final String ID_FIELD = "_id";
    private static final int HEALTH_CHECK_MAX_POOL_SIZE = 2;

    private final DittoMongoClient mongoClient;
    private final MongoCollection<Document> collection;
    private final ActorMaterializer materializer;

    /**
     * Constructs a {@code MongoClientActor}.
     */
    private MongoHealthChecker() {
        final ActorContext actorContext = getContext();
        final Config config = actorContext.system().settings().config();
        mongoClient = MongoClientWrapper.getBuilder(MongoConfig.of(config))
                .connectionPoolMaxSize(HEALTH_CHECK_MAX_POOL_SIZE)
                .build();

        /*
         * It's important to have the read preferences to primary preferred because the replication is to slow to retrieve
         * the inserted document from a secondary directly after inserting it on the primary.
         */
        collection = mongoClient.getCollection(TEST_COLLECTION_NAME)
                .withReadPreference(ReadPreference.primaryPreferred());

        materializer = ActorMaterializer.create(actorContext);
    }

    private MongoHealthChecker(final DittoMongoClient theDittoMongoClient) {
        mongoClient = theDittoMongoClient;

        /*
         * It's important to have the read preferences to primary preferred because the replication is to slow to retrieve
         * the inserted document from a secondary directly after inserting it on the primary.
         */
        collection = mongoClient.getCollection(TEST_COLLECTION_NAME)
                .withReadPreference(ReadPreference.primaryPreferred());

        materializer = ActorMaterializer.create(getContext());
    }

    /**
     * Close the Mongo client associated with this health checker, if any. Subsequent health checks fail for sure.
     */
    @Override
    public void postStop() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    /**
     * Creates Akka configuration object Props for this MongoClientActor.
     *
     * @return the Akka configuration Props object
     * @deprecated use {@link #props(org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig)} instead.
     */
    @Deprecated
    public static Props props() {
        return Props.create(MongoHealthChecker.class, MongoHealthChecker::new);
    }

    /**
     * Creates Akka configuration object Props for this MongoClientActor.
     *
     * @return the Akka configuration Props object
     */
    public static Props props(final MongoDbConfig mongoDbConfig) {
        return Props.create(MongoHealthChecker.class,
                () -> new MongoHealthChecker(MongoClientWrapper.newInstance(mongoDbConfig)));
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(RetrieveMongoStatusResponse.class, this::applyMongoStatus)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        generateStatusResponse().thenAccept(errorOpt -> {
            final RetrieveMongoStatusResponse response;
            if (errorOpt.isPresent()) {
                final Throwable error = errorOpt.get();
                response = new RetrieveMongoStatusResponse(false,
                        error.getClass().getCanonicalName() + ": " + error.getMessage());
            } else {
                response = new RetrieveMongoStatusResponse(true);
            }
            getSelf().tell(response, ActorRef.noSender());
        });
    }

    private CompletionStage<Optional<Throwable>> generateStatusResponse() {

        final String id = UUID.randomUUID().toString();

        return Source.fromPublisher(collection.insertOne(new Document(ID_FIELD, id)))
                .flatMapConcat(s ->
                        Source.fromPublisher(collection.find(eq(ID_FIELD, id))).flatMapConcat(r ->
                                Source.fromPublisher(collection.deleteOne(eq(ID_FIELD, id)))
                                        .map(DeleteResult::getDeletedCount)
                        )
                )
                .runWith(Sink.seq(), materializer)
                .handle((result, error) -> {
                    if (error != null) {
                        return Optional.of(error);
                    } else if (!Objects.equals(result, Collections.singletonList(1L))) {
                        final String message = "Expect 1 document inserted and deleted. Found: " + result;
                        return Optional.of(new IllegalStateException(message));
                    } else {
                        return Optional.empty();
                    }
                });
    }

    private void applyMongoStatus(final RetrieveMongoStatusResponse statusResponse) {
        final StatusInfo persistenceStatus = StatusInfo.fromStatus(
                statusResponse.isAlive() ? StatusInfo.Status.UP : StatusInfo.Status.DOWN,
                statusResponse.getDescription().orElse(null));
        updateHealth(persistenceStatus);
    }

}
