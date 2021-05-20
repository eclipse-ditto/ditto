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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static com.mongodb.client.model.Filters.eq;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.health.mongo.CurrentMongoStatus;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
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
    private final Materializer materializer;

    private MongoHealthChecker() {

        final DefaultMongoDbConfig mongoDbConfig = DefaultMongoDbConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));
        mongoClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                .connectionPoolMinSize(0)
                .connectionPoolMaxSize(HEALTH_CHECK_MAX_POOL_SIZE)
                .build();

        /*
         * It's important to have the read preferences to primary preferred because the replication is to slow to retrieve
         * the inserted document from a secondary directly after inserting it on the primary.
         */
        collection = mongoClient.getCollection(TEST_COLLECTION_NAME)
                .withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.LOCAL)
                .withWriteConcern(WriteConcern.ACKNOWLEDGED);

        materializer = Materializer.createMaterializer(this::getContext);
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
     * @throws NullPointerException if {@code mongoDbConfig} is {@code null}.
     */
    public static Props props() {
        return Props.create(MongoHealthChecker.class);
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(CurrentMongoStatus.class, this::applyMongoStatus)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        generateStatusResponse().thenAccept(errorOpt -> {
            final CurrentMongoStatus mongoStatus;
            if (errorOpt.isPresent()) {
                final Throwable error = errorOpt.get();
                mongoStatus = new CurrentMongoStatus(false,
                        error.getClass().getCanonicalName() + ": " + error.getMessage());
                log.error(error, error.getMessage());
            } else {
                mongoStatus = new CurrentMongoStatus(true);
            }
            getSelf().tell(mongoStatus, ActorRef.noSender());
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
                        log.error(message);
                        return Optional.of(new IllegalStateException(message));
                    } else {
                        return Optional.empty();
                    }
                });
    }

    private void applyMongoStatus(final CurrentMongoStatus status) {
        final StatusInfo persistenceStatus = StatusInfo.fromStatus(
                status.isAlive() ? StatusInfo.Status.UP : StatusInfo.Status.DOWN,
                status.getDescription().orElse(null));
        updateHealth(persistenceStatus);
    }

}
