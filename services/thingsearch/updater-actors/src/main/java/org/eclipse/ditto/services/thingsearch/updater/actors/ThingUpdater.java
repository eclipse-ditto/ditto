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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.base.actors.ShutdownBehaviour;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThing;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThingResponse;
import org.eclipse.ditto.services.thingsearch.common.config.DittoSearchConfig;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.ConsistencyLag;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;

/**
 * This Actor initiates persistence updates related to 1 thing.
 */
final class ThingUpdater extends AbstractActor {

    private static final AcknowledgementRequest SEARCH_PERSISTED_REQUEST =
            AcknowledgementRequest.of(DittoAcknowledgementLabel.SEARCH_PERSISTED);

    private final DittoDiagnosticLoggingAdapter log;
    private final ThingId thingId;
    private final ShutdownBehaviour shutdownBehaviour;
    private final ActorRef changeQueueActor;

    // state of Thing and Policy
    private long thingRevision = -1L;
    @Nullable private PolicyId policyId = null;
    private long policyRevision = -1L;

    @SuppressWarnings("unused") //It is used via reflection. See props method.
    private ThingUpdater(final ActorRef pubSubMediator, final ActorRef changeQueueActor) {
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        final DittoSearchConfig dittoSearchConfig = DittoSearchConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        thingId = tryToGetThingId();
        shutdownBehaviour = ShutdownBehaviour.fromId(thingId, pubSubMediator, getSelf());
        this.changeQueueActor = changeQueueActor;

        getContext().setReceiveTimeout(dittoSearchConfig.getUpdaterConfig().getMaxIdleTime());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param changeQueueActor reference of the change queue actor.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator, final ActorRef changeQueueActor) {

        return Props.create(ThingUpdater.class, pubSubMediator, changeQueueActor);
    }

    @Override
    public Receive createReceive() {
        return shutdownBehaviour.createReceive()
                .match(ThingEvent.class, this::processThingEvent)
                .match(ThingTag.class, this::processThingTag)
                .match(PolicyReferenceTag.class, this::processPolicyReferenceTag)
                .match(UpdateThing.class, this::updateThing)
                .match(UpdateThingResponse.class, this::processUpdateThingResponse)
                .match(ReceiveTimeout.class, this::stopThisActor)
                .matchAny(m -> {
                    log.warning("Unknown message in 'eventProcessing' behavior: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void stopThisActor(final ReceiveTimeout receiveTimeout) {
        log.debug("stopping ThingUpdater <{}> due to <{}>", thingId, receiveTimeout);
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }

    /**
     * Export the metadata of this updater.
     *
     * @param timer an optional timer measuring the search updater's consistency lag.
     */
    private Metadata exportMetadata(@Nullable final StartedTimer timer) {
        return Metadata.of(thingId, thingRevision, policyId, policyRevision, timer);
    }

    private Metadata exportMetadataWithSender(final boolean shouldAcknowledge, final ActorRef sender,
            final StartedTimer consistencyLagTimer) {
        if (shouldAcknowledge) {
            return Metadata.of(thingId, thingRevision, policyId, policyRevision, consistencyLagTimer, sender);
        } else {
            return exportMetadata(consistencyLagTimer);
        }
    }

    /**
     * Push metadata of this updater to the queue of thing-changes to be streamed into the persistence.
     */
    private void enqueueMetadata() {
        enqueueMetadata(exportMetadata(null));
    }

    private void enqueueMetadata(final Metadata metadata) {
        changeQueueActor.tell(metadata, getSelf());
    }

    private void processThingTag(final ThingTag thingTag) {
        log.debug("Received new Thing Tag for thing <{}> with revision <{}>: <{}>.",
                thingId, thingRevision, thingTag.asIdentifierString());

        if (thingTag.getRevision() > thingRevision) {
            log.debug("The Thing Tag for the thing <{}> has the revision {} which is greater than the current actor's"
                    + " sequence number <{}>.", thingId, thingTag.getRevision(), thingRevision);
            thingRevision = thingTag.getRevision();
            enqueueMetadata();
        } else {
            log.debug("Dropping <{}> because my thingRevision=<{}>", thingTag, thingRevision);
        }
        acknowledge(thingTag);
    }

    private void updateThing(final UpdateThing updateThing) {
        log.withCorrelationId(updateThing)
                .info("Requested to update search index <{}> by <{}>", updateThing, getSender());
        enqueueMetadata();
    }

    private void processUpdateThingResponse(final UpdateThingResponse response) {
        if (!response.isSuccess()) {
            final Metadata metadata = exportMetadata(null);
            log.warning("Got negative acknowledgement for <{}>; updating to <{}>.",
                    Metadata.fromResponse(response),
                    metadata);
            enqueueMetadata(metadata);
        }
    }

    private void processPolicyReferenceTag(final PolicyReferenceTag policyReferenceTag) {
        if (log.isDebugEnabled()) {
            log.debug("Received new Policy-Reference-Tag for thing <{}> with revision <{}>,  policy-id <{}> and " +
                            "policy-revision <{}>: <{}>.",
                    thingId, thingRevision, policyId, policyRevision, policyReferenceTag.asIdentifierString());
        }

        final PolicyTag policyTag = policyReferenceTag.getPolicyTag();
        final PolicyId policyIdOfTag = policyTag.getEntityId();
        if (!Objects.equals(policyId, policyIdOfTag) || policyRevision < policyTag.getRevision()) {
            this.policyId = policyIdOfTag;
            policyRevision = policyTag.getRevision();
            enqueueMetadata();
        } else {
            log.debug("Dropping <{}> because my policyId=<{}> and policyRevision=<{}>",
                    policyReferenceTag, policyId, policyRevision);
        }
        acknowledge(policyReferenceTag);
    }

    private void processThingEvent(final ThingEvent<?> thingEvent) {
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(thingEvent);
        l.debug("Received new thing event for thing id <{}> with revision <{}>.", thingId, thingEvent.getRevision());
        final boolean shouldAcknowledge =
                thingEvent.getDittoHeaders().getAcknowledgementRequests().contains(SEARCH_PERSISTED_REQUEST);

        // check if the revision is valid (thingEvent.revision = 1 + sequenceNumber)
        if (thingEvent.getRevision() <= thingRevision && !shouldAcknowledge) {
            l.debug("Dropped thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor.", thingId,
                    thingEvent.getRevision(), thingRevision);
        } else {
            l.debug("Applying thing event <{}>.", thingEvent);
            thingRevision = thingEvent.getRevision();
            final StartedTimer timer = DittoMetrics.timer(ConsistencyLag.TIMER_NAME)
                    .tag(ConsistencyLag.TAG_SHOULD_ACK, Boolean.toString(shouldAcknowledge))
                    .onExpiration(startedTimer ->
                            l.warning("Timer measuring consistency lag timed out for event <{}>",
                            thingEvent))
                    .start();
            ConsistencyLag.startS0InUpdater(timer);
            enqueueMetadata(exportMetadataWithSender(shouldAcknowledge, getSender(), timer));
        }
    }

    private ThingId tryToGetThingId() {
        final Charset utf8 = StandardCharsets.UTF_8;
        try {
            return getThingId(utf8);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(MessageFormat.format("Charset <{0}> is unsupported!", utf8.name()), e);
        }
    }

    private ThingId getThingId(final Charset charset) throws UnsupportedEncodingException {
        final String actorName = self().path().name();
        return ThingId.of(URLDecoder.decode(actorName, charset.name()));
    }

    private void acknowledge(final IdentifiableStreamingMessage message) {
        final ActorRef sender = getSender();
        if (!getContext().system().deadLetters().equals(sender)) {
            getSender().tell(StreamAck.success(message.asIdentifierString()), getSelf());
        }
    }

}
