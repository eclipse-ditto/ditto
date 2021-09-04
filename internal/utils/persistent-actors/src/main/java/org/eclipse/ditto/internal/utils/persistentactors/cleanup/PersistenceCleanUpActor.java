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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import java.time.Duration;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.json.JsonObject;

import akka.Done;
import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;

/**
 * Actor to control persistence cleanup.
 */
public final class PersistenceCleanUpActor extends AbstractFSM<PersistenceCleanUpActor.State, String> {

    /**
     * Name of this actor.
     */
    public static final String NAME = "persistenceCleanUp";

    private final ThreadSafeDittoLoggingAdapter logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
    private final Materializer materializer = Materializer.createMaterializer(getContext());
    private final Duration quietPeriod;
    private final CleanUp cleanUp;
    private final Credits credits;

    PersistenceCleanUpActor(final Duration quietPeriod, final CleanUp cleanUp, final Credits credits) {
        this.quietPeriod = quietPeriod;
        this.cleanUp = cleanUp;
        this.credits = credits;
    }

    @SuppressWarnings("unused") // called by reflection
    private PersistenceCleanUpActor(final CleanUpConfig config,
            final MongoReadJournal mongoReadJournal,
            final String myRole) {
        final var cluster = Cluster.get(getContext().getSystem());
        final var responsibilitySupplier = ClusterResponsibilitySupplier.of(cluster, myRole);
        quietPeriod = config.getQuietPeriod();
        cleanUp = CleanUp.of(config, mongoReadJournal, materializer, responsibilitySupplier);
        credits = Credits.of(config);
    }

    /**
     * Create the Props object for this actor.
     *
     * @param config the background clean-up config.
     * @param mongoReadJournal the Mongo read journal for databasae operations.
     * @param myRole the cluster role of this node among which the background cleanup responsibility is divided.
     * @return the Props object.
     */
    public static Props props(final CleanUpConfig config,
            final MongoReadJournal mongoReadJournal,
            final String myRole) {

        return Props.create(PersistenceCleanUpActor.class, config, mongoReadJournal, myRole);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        startWith(State.IN_QUIET_PERIOD, "", randomizeQuietPeriod());
        when(State.IN_QUIET_PERIOD, inQuietPeriod());
        when(State.RUNNING, running());
        whenUnhandled(inAnyState());
        initialize();
    }

    private FSMStateFunctionBuilder<State, String> inQuietPeriod() {
        return matchEventEquals(StateTimeout(), this::startStream);
    }

    private FSMStateFunctionBuilder<State, String> running() {
        return matchEvent(CleanUpResult.class, this::logCleanUpResult)
                .eventEquals(Control.STREAM_COMPLETE, this::streamComplete)
                .eventEquals(Control.STREAM_FAILED, this::streamFailed);
    }

    private FSMStateFunctionBuilder<State, String> inAnyState() {
        return matchEvent(RetrieveHealth.class, this::retrieveHealth)
                .anyEvent((message, lastPid) -> {
                    logger.warning("Got unhandled message <{}> when state=<{}> lastPid=<{}>",
                            message, stateName().name(), lastPid);
                    return stay();
                });
    }

    private FSM.State<State, String> startStream(final StateTimeout$ stateTimeout, final String lastPid) {
        logger.info("Quiet period expired, starting stream from <{}>", lastPid);
        credits.regulate(cleanUp.getCleanUpStream(lastPid), logger)
                .flatMapConcat(workUnit -> workUnit)
                .toMat(Sink.foreach(this::notifySelf), Keep.right())
                .withAttributes(Attributes.inputBuffer(1, 1))
                .run(materializer)
                .handle(this::streamCompletedOrFailed);
        return goTo(State.RUNNING);
    }

    private FSM.State<State, String> logCleanUpResult(final CleanUpResult result, final String lastPid) {
        logger.debug("CleanUpResult=<{}>", result);
        final var nextPid = result.snapshotRevision.pid;
        if (!lastPid.equals(nextPid)) {
            logger.info("Progress=<{}>", nextPid);
        }
        return stay().using(nextPid);
    }

    @SuppressWarnings("unused")
    private FSM.State<State, String> streamComplete(final Control streamComplete, final String lastPid) {
        final var nextQuietPeriod = randomizeQuietPeriod();
        logger.info("Stream complete. Next stream in <{}>", nextQuietPeriod);
        return goTo(State.IN_QUIET_PERIOD).forMax(nextQuietPeriod).using("");
    }

    private FSM.State<State, String> streamFailed(final Control streamComplete, final String lastPid) {
        final var nextQuietPeriod = randomizeQuietPeriod();
        logger.info("Stream failed. Next stream in <{}> starting from <{}>", nextQuietPeriod, lastPid);
        return goTo(State.IN_QUIET_PERIOD).forMax(nextQuietPeriod).using(lastPid);
    }

    private FSM.State<State, String> retrieveHealth(final RetrieveHealth retrieveHealth, final String lastPid) {
        final var response = RetrieveHealthResponse.of(
                StatusInfo.fromDetail(StatusDetailMessage.of(StatusDetailMessage.Level.INFO, JsonObject.newBuilder()
                        .set("state", stateName().name())
                        .set("pid", lastPid)
                        .build())
                ),
                DittoHeaders.empty()
        );
        getSender().tell(response, getSelf());
        return stay();
    }

    private Duration randomizeQuietPeriod() {
        final long divisor = 1024;
        final long multiplier = (long) (Math.random() * divisor);
        return quietPeriod.plus(quietPeriod.multipliedBy(multiplier).dividedBy(divisor));
    }

    private void notifySelf(final CleanUpResult result) {
        getSelf().tell(result, ActorRef.noSender());
    }

    private Done streamCompletedOrFailed(@Nullable final Done done, @Nullable final Throwable error) {
        if (error == null) {
            getSelf().tell(Control.STREAM_COMPLETE, ActorRef.noSender());
        } else {
            logger.error(error, "Stream failed");
            getSelf().tell(Control.STREAM_FAILED, ActorRef.noSender());
        }
        return Done.getInstance();
    }

    private enum Control {
        STREAM_COMPLETE,
        STREAM_FAILED
    }

    /**
     * State of the persistence cleanup actor.
     */
    public enum State {
        IN_QUIET_PERIOD,
        RUNNING
    }
}
