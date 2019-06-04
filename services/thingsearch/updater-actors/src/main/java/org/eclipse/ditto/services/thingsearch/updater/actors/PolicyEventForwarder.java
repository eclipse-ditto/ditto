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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static akka.cluster.pubsub.DistributedPubSubMediator.Subscribe;
import static akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.thingsearch.common.config.DittoSearchConfig;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.DelayOverflowStrategy;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Cluster singleton that forwards policy events to updater shard region with buffering.
 */
final class PolicyEventForwarder extends AbstractActor {

    private static final Duration ASK_SELF_TIMEOUT = Duration.ofSeconds(10L);

    static final String ACTOR_NAME = "thingsSearchPolicyEventForwarder";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorMaterializer materializer = ActorMaterializer.create(getContext());

    private final ActorRef thingsUpdater;
    private final ThingsSearchUpdaterPersistence persistence;
    private final BlockNamespaceBehavior blockNamespaceBehavior;
    private final Duration interval;

    private Map<String, Long> policyRevisions = new HashMap<>();
    private KillSwitch killSwitch;

    @SuppressWarnings("unused")
    private PolicyEventForwarder(final ActorRef pubSubMediator,
            final ActorRef thingsUpdater,
            final BlockedNamespaces blockedNamespaces,
            final ThingsSearchUpdaterPersistence persistence) {

        this.thingsUpdater = thingsUpdater;
        this.persistence = persistence;
        blockNamespaceBehavior = BlockNamespaceBehavior.of(blockedNamespaces);
        interval = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                .getStreamConfig().getWriteInterval();

        final Subscribe subscribe = new Subscribe(PolicyEvent.TYPE_PREFIX, ACTOR_NAME, getSelf());
        pubSubMediator.tell(subscribe, getSelf());

        restartPolicyReferenceTagStream();
    }

    /**
     * Create Props for this cluster singleton.
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

        return Props.create(PolicyEventForwarder.class, pubSubMediator, thingsUpdater, blockedNamespaces, persistence);
    }

    @Override
    public void postStop() throws Exception {
        terminateStream();
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(PolicyEvent.class, this::policyEvent)
                .match(PolicyTag.class, this::updatePolicyRevision)
                .match(PolicyReferenceTag.class, this::forwardToThingsUpdater)
                .matchEquals(Control.DUMP_POLICY_REVISIONS, this::dumpPolicyRevisions)
                .matchEquals(Control.STREAM_COMPLETED, this::streamTerminated)
                .match(Status.Failure.class, this::streamTerminated)
                .match(SubscribeAck.class, this::subscribeAck)
                .build();
    }

    /**
     * Convert policy event to policy tag after blocking purged namespace.
     *
     * @param policyEvent incoming policy event
     */
    private void policyEvent(final PolicyEvent policyEvent) {
        final ActorRef self = getSelf();
        blockNamespaceBehavior.block(policyEvent)
                .whenComplete((result, error) -> {
                    if (error == null) {
                        self.tell(PolicyTag.of(policyEvent.getPolicyId(), policyEvent.getRevision()), self);
                    }
                });
    }

    private void updatePolicyRevision(final PolicyTag policyTag) {
        final String policyId = policyTag.getId();
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
        thingsUpdater.tell(policyReferenceTag, ActorRef.noSender());
    }

    @SuppressWarnings("unused")
    private void dumpPolicyRevisions(final Control trigger) {
        final Map<String, Long> dump = policyRevisions;
        policyRevisions = new HashMap<>();
        getSender().tell(dump, getSelf());
    }

    private void streamTerminated(final Object streamTerminated) {
        if (streamTerminated instanceof Status.Failure) {
            final Status.Failure failure = (Status.Failure) streamTerminated;
            final String errorMessage = "PolicyEventForwarder stream terminated (should NEVER happen!), restarting";
            log.error(failure.cause(), errorMessage);
        } else {
            log.info("PolicyEventForwarder stream completed; restarting");
        }
        restartPolicyReferenceTagStream();
    }

    private void subscribeAck(final SubscribeAck subscribeAck) {
        log.info("SubscribeAck: <{}>", subscribeAck);
    }

    private void restartPolicyReferenceTagStream() {
        terminateStream();
        final ActorRef self = getSelf();
        killSwitch = Source.repeat(Control.DUMP_POLICY_REVISIONS)
                .delay(interval, DelayOverflowStrategy.backpressure())
                .withAttributes(Attributes.inputBuffer(1, 1))
                .viaMat(KillSwitches.single(), Keep.right())
                .mapAsync(1, message ->
                        PatternsCS.ask(self, message, ASK_SELF_TIMEOUT).exceptionally(Function.identity()))
                .flatMapConcat(this::mapDumpResult)
                .to(Sink.actorRef(self, Control.STREAM_COMPLETED))
                .run(materializer);
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
            return persistence.getPolicyReferenceTags((Map<String, Long>) dumpResult);
        } else {
            if (dumpResult instanceof Throwable) {
                log.error((Throwable) dumpResult, "dump failed");
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

}
