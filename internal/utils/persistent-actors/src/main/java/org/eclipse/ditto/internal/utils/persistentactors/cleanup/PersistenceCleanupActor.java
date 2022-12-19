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
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.common.ModifyConfig;
import org.eclipse.ditto.base.api.common.RetrieveConfig;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.actors.ModifyConfigBehavior;
import org.eclipse.ditto.internal.utils.akka.actors.RetrieveConfigBehavior;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.CoordinatedShutdown;
import akka.actor.FSM;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.Pair;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import akka.stream.Attributes;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;

/**
 * Actor to control persistence cleanup.
 */
public final class PersistenceCleanupActor extends AbstractFSM<PersistenceCleanupActor.State, String>
        implements RetrieveConfigBehavior, ModifyConfigBehavior {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "persistenceCleanup";

    /**
     * JSON field of ModifyConfig commands that
     */
    private static final String SET_LAST_PID = "last-pid";

    /**
     * Ask-timeout in shutdown tasks. Its duration should be long enough but ultimately does not
     * matter because each shutdown phase has its own timeout.
     */
    private static final Duration SHUTDOWN_ASK_TIMEOUT = Duration.ofMinutes(2L);

    private static final Throwable KILL_SWITCH_EXCEPTION =
            new IllegalStateException("Aborting persistence clean up stream because of graceful shutdown.");

    private final ThreadSafeDittoLoggingAdapter logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
    private final Materializer materializer = Materializer.createMaterializer(getContext());
    private final Counter deleteEventsCounter = DittoMetrics.counter("cleanup_delete_events");
    private final Counter deleteSnapsCounter = DittoMetrics.counter("cleanup_delete_snapshots");
    private final MongoReadJournal mongoReadJournal;
    private final Supplier<Pair<Integer, Integer>> responsibilitySupplier;

    private CleanupConfig config;
    private Cleanup cleanup;
    private Credits credits;
    @Nullable private UniqueKillSwitch killSwitch = null;

    PersistenceCleanupActor(final Cleanup cleanup,
            final Credits credits,
            final MongoReadJournal mongoReadJournal,
            final Supplier<Pair<Integer, Integer>> responsibilitySupplier) {
        this.config = CleanupConfig.of(ConfigFactory.empty());
        this.cleanup = cleanup;
        this.credits = credits;
        this.mongoReadJournal = mongoReadJournal;
        this.responsibilitySupplier = responsibilitySupplier;
    }

    @SuppressWarnings("unused") // called by reflection
    private PersistenceCleanupActor(final CleanupConfig config,
            final MongoReadJournal mongoReadJournal,
            final String myRole) {
        final var cluster = Cluster.get(getContext().getSystem());
        this.mongoReadJournal = mongoReadJournal;
        responsibilitySupplier = ClusterResponsibilitySupplier.of(cluster, myRole);
        this.config = config;
        cleanup = Cleanup.of(config, mongoReadJournal, materializer, responsibilitySupplier);
        credits = Credits.of(config);
    }

    /**
     * Create the Props object for this actor.
     *
     * @param config the background cleanup config.
     * @param mongoReadJournal the Mongo read journal for database operations.
     * @param myRole the cluster role of this node among which the background cleanup responsibility is divided.
     * @return the Props object.
     */
    public static Props props(final CleanupConfig config, final MongoReadJournal mongoReadJournal,
            final String myRole) {

        return Props.create(PersistenceCleanupActor.class, config, mongoReadJournal, myRole);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        final var coordinatedShutdown = CoordinatedShutdown.get(getContext().getSystem());
        final var serviceRequestsDoneTask = "service-requests-done-" + ACTOR_NAME;
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone(), serviceRequestsDoneTask,
                () -> Patterns.ask(getSelf(), Control.SERVICE_REQUESTS_DONE, SHUTDOWN_ASK_TIMEOUT)
                        .thenApply(reply -> Done.done())
        );

        if (config.isEnabled()) {
            startWith(State.IN_QUIET_PERIOD, "", randomizeQuietPeriod());
        } else {
            startWith(State.IN_QUIET_PERIOD, "");
        }
        when(State.IN_QUIET_PERIOD, inQuietPeriod());
        when(State.RUNNING, running());
        whenUnhandled(inAnyState());
        initialize();
    }

    private FSMStateFunctionBuilder<State, String> inQuietPeriod() {
        return matchEventEquals(StateTimeout(), this::startStream)
                .eventEquals(Control.SHUTDOWN, this::shutdownInQuietPeriod)
                .eventEquals(Control.SERVICE_REQUESTS_DONE, this::serviceRequestsDone);
    }

    private FSMStateFunctionBuilder<State, String> running() {
        return matchEvent(CleanupResult.class, this::logCleanupResult)
                .eventEquals(Control.STREAM_COMPLETE, this::streamComplete)
                .eventEquals(Control.STREAM_FAILED, this::streamFailed)
                .eventEquals(Control.SHUTDOWN, this::shutdownRunningStream)
                .eventEquals(Control.SERVICE_REQUESTS_DONE, this::serviceRequestsDone);
    }

    private FSMStateFunctionBuilder<State, String> inAnyState() {
        return matchEvent(RetrieveHealth.class, this::retrieveHealth)
                .event(RetrieveConfig.class, (retrieveConfig, lastPid) -> {
                    retrieveConfigBehavior().onMessage().apply(retrieveConfig);

                    return stay();
                })
                .event(ModifyConfig.class, (modifyConfig, lastPid) -> {
                    modifyConfigBehavior().onMessage().apply(modifyConfig);
                    final var setLastPid = modifyConfig.getConfig()
                            .getValue(SET_LAST_PID)
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString);
                    final var stay = stay();

                    return setLastPid.map(stay::using).orElse(stay);
                })
                .eventEquals(Control.SERVICE_REQUESTS_DONE, this::serviceRequestsDone)
                .anyEvent((message, lastPid) -> {
                    logger.warning("Got unhandled message <{}> when state=<{}> lastPid=<{}>",
                            message, stateName().name(), lastPid);

                    return stay();
                });
    }

    private FSM.State<State, String> startStream(final StateTimeout$ stateTimeout, final String lastPid) {
        logger.info("Quiet period expired, starting stream from <{}>", lastPid);
        final var materializedValues =
                credits.regulate(cleanup.getCleanupStream(lastPid), logger)
                        .flatMapConcat(workUnit -> workUnit)
                        .viaMat(KillSwitches.single(), Keep.right())
                        .toMat(Sink.foreach(this::notifySelf), Keep.both())
                        .withAttributes(Attributes.inputBuffer(1, 1))
                        .run(materializer);

        killSwitch = materializedValues.first();
        materializedValues.second().handle(this::streamCompletedOrFailed);

        return goTo(State.RUNNING);
    }

    private FSM.State<State, String> logCleanupResult(final CleanupResult result, final String lastPid) {
        logger.debug("CleanupResult=<{}>", result);
        final var nextPid = result.snapshotRevision.pid;
        if (!lastPid.equals(nextPid)) {
            logger.info("Progress=<{}>", nextPid);
        }
        switch (result.type) {
            case SNAPSHOTS:
                deleteSnapsCounter.increment(result.result.getDeletedCount());
                break;
            case EVENTS:
            default:
                deleteEventsCounter.increment(result.result.getDeletedCount());
                break;
        }

        return stay().using(nextPid);
    }

    private FSM.State<State, String> streamComplete(final Control streamComplete, final String lastPid) {
        final var result = goTo(State.IN_QUIET_PERIOD).using("");
        if (config.isEnabled()) {
            final var nextQuietPeriod = randomizeQuietPeriod();
            logger.info("Stream complete. Next stream in <{}> from start", nextQuietPeriod);

            return result.forMax(nextQuietPeriod);
        } else {
            logger.info("Stream complete and disabled.");

            return result;
        }
    }

    private FSM.State<State, String> streamFailed(final Control streamComplete, final String lastPid) {
        final var result = goTo(State.IN_QUIET_PERIOD).using(lastPid);
        if (config.isEnabled()) {
            final var nextQuietPeriod = randomizeQuietPeriod();
            logger.info("Stream failed or shutdown. Next stream in <{}> starting from <{}>", nextQuietPeriod,
                    lastPid);

            return result.forMax(nextQuietPeriod);
        } else {
            logger.info("Stream failed or shutdown and disabled. Last PID=<{}>", lastPid);

            return result;
        }
    }

    private FSM.State<State, String> shutdownRunningStream(final Control shutdown, final String lastPid) {
        logger.info("Activating kill-switch on demand: <{}>", killSwitch);
        if (killSwitch != null) {
            // using ABORT to preserve lastPid
            killSwitch.abort(KILL_SWITCH_EXCEPTION);
        }

        return stay();
    }

    private FSM.State<State, String> shutdownInQuietPeriod(final Control shutdown, final String lastPid) {
        if (config.isEnabled()) {
            logger.info("Starting stream from <{}> in <{}> on request", lastPid, config.getQuietPeriod());

            return goTo(State.IN_QUIET_PERIOD).forMax(config.getQuietPeriod());
        } else {
            logger.info("Stream disabled. lastPid=<{}>", lastPid);

            return goTo(State.IN_QUIET_PERIOD);
        }
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
        final var quietPeriod = config.getQuietPeriod();

        return quietPeriod.plus(quietPeriod.multipliedBy(multiplier).dividedBy(divisor));
    }

    private void notifySelf(final CleanupResult result) {
        getSelf().tell(result, ActorRef.noSender());
    }

    private Done streamCompletedOrFailed(@Nullable final Done done, @Nullable final Throwable error) {
        if (error == null) {
            getSelf().tell(Control.STREAM_COMPLETE, ActorRef.noSender());
        } else {
            if (error != KILL_SWITCH_EXCEPTION) {
                logger.error(error, "Stream failed");
            }
            getSelf().tell(Control.STREAM_FAILED, ActorRef.noSender());
        }
        killSwitch = null;

        return Done.getInstance();
    }

    @Override
    public Config getConfig() {
        return config.render();
    }

    @Override
    public Config setConfig(final Config config) {
        this.config = this.config.setAll(config);
        cleanup = Cleanup.of(this.config, mongoReadJournal, materializer, responsibilitySupplier);
        credits = Credits.of(this.config);
        getSelf().tell(Control.SHUTDOWN, ActorRef.noSender());

        return this.config.render();
    }

    private FSM.State<State, String> serviceRequestsDone(final Control serviceRequestsDone, final String lastPid) {
        if (killSwitch != null) {
            logger.info("Aborting stream because of graceful shutdown.");
            killSwitch.abort(KILL_SWITCH_EXCEPTION);
        }
        getSender().tell(Done.getInstance(), getSelf());

        return stay();
    }

    public enum Control {
        STREAM_COMPLETE,
        STREAM_FAILED,
        SHUTDOWN,
        SERVICE_REQUESTS_DONE
    }

    /**
     * State of the persistence cleanup actor.
     */
    public enum State {
        IN_QUIET_PERIOD,
        RUNNING
    }

}
