/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import static akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.ThingsSearchUpdaterPersistence;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor that forwards policy tags (emitted by PolicyPersistence after each policy modification) to updater
 * shard region with buffering.
 */
final class PolicyModificationForwarder extends AbstractActor {

    private static final Duration ASK_SELF_TIMEOUT = Duration.ofSeconds(10L);

    static final String ACTOR_NAME = "policyModificationForwarder";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef pubSubMediator;
    private final ActorRef thingsUpdater;
    private final ThingsSearchUpdaterPersistence persistence;
    private final BlockNamespaceBehavior blockNamespaceBehavior;
    private final Duration interval;

    private Map<PolicyId, Long> policyRevisions = new HashMap<>();
    @Nullable private KillSwitch killSwitch;

    @SuppressWarnings("unused")
    private PolicyModificationForwarder(final ActorRef pubSubMediator,
            final ActorRef thingsUpdater,
            final BlockedNamespaces blockedNamespaces,
            final ThingsSearchUpdaterPersistence persistence) {

        this.pubSubMediator = pubSubMediator;
        this.thingsUpdater = thingsUpdater;
        this.persistence = persistence;
        blockNamespaceBehavior = BlockNamespaceBehavior.of(blockedNamespaces);
        interval = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                .getUpdaterConfig().getStreamConfig().getWriteInterval();

        pubSubMediator.tell(DistPubSubAccess.subscribeViaGroup(PolicyTag.PUB_SUB_TOPIC_MODIFIED, ACTOR_NAME, getSelf()),
                getSelf());

        restartPolicyReferenceTagStream();
    }

    /**
     * Create Props for this actor.
     *
     * @param pubSubMediator Akka pub-sub-mediator
     * @param thingsUpdater thingsUpdater
     * @param blockedNamespaces blocked namespaces.
     * @return the Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef thingsUpdater,
            final BlockedNamespaces blockedNamespaces,
            final ThingsSearchUpdaterPersistence persistence) {

        return Props.create(PolicyModificationForwarder.class, pubSubMediator, thingsUpdater, blockedNamespaces,
                persistence);
    }

    @Override
    public void preStart() {
        CoordinatedShutdown.get(getContext().getSystem())
                .addTask(CoordinatedShutdown.PhaseServiceUnbind(), "service-unbind-" + ACTOR_NAME, () -> {
                    final var unsub =
                            DistPubSubAccess.unsubscribeViaGroup(PolicyTag.PUB_SUB_TOPIC_MODIFIED, ACTOR_NAME, getSelf());
                    final var shutdownAskTimeout = Duration.ofMinutes(1); // does not matter as phase will timeout

                    return Patterns.ask(pubSubMediator, unsub, shutdownAskTimeout).thenApply(reply -> Done.done());
                });
    }

    @Override
    public void postStop() {
        terminateStream();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(PolicyTag.class, this::policyTag)
                .match(LocalWrappedPolicyTag.class, this::updatePolicyRevision)
                .match(PolicyReferenceTag.class, this::forwardToThingsUpdater)
                .matchEquals(Control.DUMP_POLICY_REVISIONS, this::dumpPolicyRevisions)
                .matchEquals(Control.STREAM_COMPLETED, this::streamTerminated)
                .match(Status.Failure.class, this::streamTerminated)
                .match(SubscribeAck.class, this::subscribeAck)
                .build();
    }

    /**
     * Apply PolicyTag after checking for blocking purged namespace.
     *
     * @param policyTag incoming policy tag
     */
    private void policyTag(final PolicyTag policyTag) {
        final ActorRef self = getSelf();
        blockNamespaceBehavior.isBlocked(policyTag.getEntityId())
                .whenComplete((isBlocked, error) -> {
                    if (null == error && !isBlocked) {
                        self.tell(new LocalWrappedPolicyTag(policyTag), self);
                    }
                });
    }

    private void updatePolicyRevision(final LocalWrappedPolicyTag wrappedPolicyTag) {
        final PolicyTag policyTag = wrappedPolicyTag.delegate();
        final PolicyId policyId = policyTag.getEntityId();
        final long revision = policyTag.getRevision();
        policyRevisions.merge(policyId, revision, Long::max);
    }

    /**
     * Forward PolicyReferenceTag to ThingsUpdater, who then forward it to shard region after namespace blocking.
     * There should be no sender because this actor expects no acknowledgement.
     *
     * @param policyReferenceTag the policy reference tag.
     */
    private void forwardToThingsUpdater(final PolicyReferenceTag policyReferenceTag) {
        log.info("Forwarding <{}> at <{}> to <{}>", policyReferenceTag.getPolicyTag().getEntityId(),
                policyReferenceTag.getPolicyTag().getRevision(),
                policyReferenceTag.getThingId());
        thingsUpdater.tell(policyReferenceTag, ActorRef.noSender());
    }

    @SuppressWarnings("unused")
    private void dumpPolicyRevisions(final Control trigger) {
        final Map<PolicyId, Long> dump = policyRevisions;
        policyRevisions = new HashMap<>();
        getSender().tell(dump, getSelf());
    }

    private void streamTerminated(final Object streamTerminated) {
        if (streamTerminated instanceof Status.Failure failure) {
            final String errorMessage =
                    "PolicyModificationForwarder stream terminated (should NEVER happen!), restarting";
            log.error(failure.cause(), errorMessage);
        } else {
            log.info("PolicyModificationForwarder stream completed; restarting");
        }
        restartPolicyReferenceTagStream();
    }

    private void subscribeAck(final SubscribeAck subscribeAck) {
        log.info("SubscribeAck: <{}>", subscribeAck);
    }

    private void restartPolicyReferenceTagStream() {
        terminateStream();
        final ActorRef self = getSelf();

        final Source<Control, NotUsed> repeat;
        if (!interval.isNegative() && !interval.isZero()) {
            repeat = Source.repeat(Control.DUMP_POLICY_REVISIONS)
                    .throttle(1, interval);
        } else {
            repeat = Source.repeat(Control.DUMP_POLICY_REVISIONS);
        }
        killSwitch = repeat.viaMat(KillSwitches.single(), Keep.right())
                .mapAsync(1, message ->
                        Patterns.ask(self, message, ASK_SELF_TIMEOUT).exceptionally(Function.identity()))
                .flatMapConcat(this::mapDumpResult)
                .to(Sink.actorRef(self, Control.STREAM_COMPLETED))
                .run(getContext().getSystem());
    }

    private void terminateStream() {
        if (killSwitch != null) {
            killSwitch.shutdown();
            killSwitch = null;
        }
    }

    @SuppressWarnings("unchecked")
    private Source<PolicyReferenceTag, NotUsed> mapDumpResult(final Object dumpResult) {
        if (dumpResult instanceof Map) {
            final Map<PolicyId, Long> map = (Map<PolicyId, Long>) dumpResult;
            if (map.isEmpty()) {
                return Source.empty();
            } else {
                return persistence.getPolicyReferenceTags(map);
            }
        } else {
            if (dumpResult instanceof Throwable throwable) {
                log.error(throwable, "dump failed");
            } else {
                log.warning("Unexpected dump result: <{}>", dumpResult);
            }
            return Source.empty();
        }
    }

    private enum Control {
        DUMP_POLICY_REVISIONS,
        STREAM_COMPLETED
    }

    private record LocalWrappedPolicyTag(PolicyTag delegate) {}

}
