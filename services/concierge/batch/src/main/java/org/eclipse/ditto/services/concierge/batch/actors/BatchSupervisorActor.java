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
package org.eclipse.ditto.services.concierge.batch.actors;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatch;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.batch.BatchExecutionFinished;
import org.eclipse.ditto.signals.events.batch.BatchExecutionStarted;

import com.mongodb.DBObject;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotOffer;
import scala.Option;

/**
 * Actor which supervises {@link BatchCoordinatorActor}.
 */
public final class BatchSupervisorActor extends AbstractPersistentActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "batchSupervisor";

    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-batch-supervisor-journal";
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-batch-supervisor-snapshots";
    private static final int SNAPSHOT_THRESHOLD = 1000;

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ActorRef conciergeForwarder;
    private final SnapshotAdapter<Set<String>> snapshotAdapter;
    private long snapshotSequenceNr = -1;

    private Set<String> batchIds;

    private BatchSupervisorActor( final ActorRef pubSubMediator, final ActorRef conciergeForwarder) {
        this.pubSubMediator = pubSubMediator;
        this.conciergeForwarder = conciergeForwarder;

        snapshotAdapter = new BatchIdsSnapshotAdapter();
        batchIds = new HashSet<>();
    }

    /**
     * Creates Akka configuration object Props for this BatchSupervisorActor.
     *
     * @param pubSubMediator the mediator to use for distributed pubsub.
     * @param conciergeForwarder the ref of the conciergeForwarder.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef conciergeForwarder) {
        return Props.create(BatchSupervisorActor.class, new Creator<BatchSupervisorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public BatchSupervisorActor create() {
                return new BatchSupervisorActor(pubSubMediator, conciergeForwarder);
            }
        });
    }

    @Override
    public String persistenceId() {
        return ACTOR_NAME;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
                .match(SnapshotOffer.class, ss -> {
                    batchIds = snapshotAdapter.fromSnapshotStore(ss);
                    snapshotSequenceNr = ss.metadata().sequenceNr();
                })
                .match(BatchExecutionStarted.class, event -> batchIds.add(event.getBatchId()))
                .match(BatchExecutionFinished.class, event -> batchIds.remove(event.getBatchId()))
                .match(RecoveryCompleted.class, rc -> {
                    log.debug("Recovery completed");
                    batchIds.forEach(this::lookupBatchCoordinatorActor);
                })
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ExecuteBatch.class, this::forwardCommand)
                .match(BatchExecutionStarted.class, event -> persistEvent(event, e -> batchIds.add(e.getBatchId())))
                .match(BatchExecutionFinished.class, event -> persistEvent(event, e -> batchIds.remove(e.getBatchId())))
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .match(SaveSnapshotSuccess.class, ss -> {
                    snapshotSequenceNr = ss.metadata().sequenceNr();
                    deleteMessages(snapshotSequenceNr - 1);
                })
                .match(SaveSnapshotFailure.class,
                        sf -> log.error(sf.cause(), "Failed to save Snapshot. Cause: {}.", sf.cause().getMessage()))
                .matchAny(m -> log.warning("Got unknown message, expected an 'ExecuteBatch' command: {}", m))
                .build();
    }

    @Override
    public void preStart() {
        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(BatchExecutionStarted.TYPE, ACTOR_NAME, getSelf()),
                getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(BatchExecutionFinished.TYPE, ACTOR_NAME, getSelf()),
                getSelf());
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(true, DeciderBuilder
                .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                .match(ActorKilledException.class, e -> SupervisorStrategy.stop())
                .matchAny(e -> SupervisorStrategy.escalate())
                .build());
    }

    private void forwardCommand(final ExecuteBatch command) {
        final String batchId = command.getDittoHeaders()
                .getCorrelationId()
                .orElse(UUID.randomUUID().toString());

        final ActorRef batchCoordinatorActor = lookupBatchCoordinatorActor(batchId);
        batchCoordinatorActor.forward(command, getContext());
    }

    private ActorRef lookupBatchCoordinatorActor(final String batchId) {
        final Option<ActorRef> batchCoordinatorActor =
                getContext().child(BatchCoordinatorActor.ACTOR_NAME_PREFIX + batchId);
        if (batchCoordinatorActor.isDefined()) {
            return batchCoordinatorActor.get();
        } else {
            final Props props = BatchCoordinatorActor.props(batchId, pubSubMediator, conciergeForwarder);
            return getContext().actorOf(props, BatchCoordinatorActor.ACTOR_NAME_PREFIX + batchId);
        }
    }

    private <E extends Event> void persistEvent(final E event, final Consumer<E> consumer) {
        log.debug("Persisting Event '{}'", event.getType());

        persist(event, persistedEvent -> {
            log.debug("Successfully persisted Event '{}'", event.getType());

            // save a snapshot if there were too many changes since the last snapshot
            if ((lastSequenceNr() - snapshotSequenceNr) > SNAPSHOT_THRESHOLD) {
                final Object snapshotToStore = snapshotAdapter.toSnapshotStore(batchIds);
                saveSnapshot(snapshotToStore);
            }

            consumer.accept(persistedEvent);
        });
    }

    static final class BatchIdsSnapshotAdapter implements SnapshotAdapter<Set<String>> {

        @Override
        public Object toSnapshotStore(final Set<String> snapshot) {
            final JsonArray jsonValues = snapshot.stream()
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());
            return DittoBsonJson.getInstance().parse(jsonValues);
        }

        @Override
        public Set<String> fromSnapshotStore(final SnapshotOffer snapshotOffer) {
            final Object snapshotEntityFromDb = snapshotOffer.snapshot();

            if (snapshotEntityFromDb instanceof DBObject) {
                final DBObject dbObject = (DBObject) snapshotEntityFromDb;
                final JsonArray jsonValues = JsonFactory.newArray(DittoBsonJson.getInstance().serialize(dbObject).toString());
                return jsonValues.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toSet());
            } else {
                throw new IllegalArgumentException("Unable to fromSnapshotStore a non-'DBObject' object! Was: " +
                        snapshotEntityFromDb.getClass());
            }
        }

        @Nullable
        @Override
        public Set<String> fromSnapshotStore(final SelectedSnapshot selectedSnapshot) {
            return null;
        }

    }

}
