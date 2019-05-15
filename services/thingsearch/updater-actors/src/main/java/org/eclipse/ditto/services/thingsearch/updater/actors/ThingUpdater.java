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

import org.eclipse.ditto.services.base.actors.ShutdownNamespaceBehavior;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;

import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;

/**
 * This Actor initiates persistence updates related to 1 thing.
 */
final class ThingUpdater extends AbstractActor {

    private final DiagnosticLoggingAdapter log = Logging.apply(this);

    private final String thingId;
    private final ShutdownNamespaceBehavior shutdownNamespaceBehavior;
    private final ActorRef changeQueueActor;

    // state of Thing and Policy
    private long thingRevision = -1L;
    private String policyId = "";
    private long policyRevision = -1L;

    private ThingUpdater(final ActorRef pubSubMediator,
            final ActorRef changeQueueActor,
            final java.time.Duration maxIdleTime) {

        thingId = tryToGetThingId();
        shutdownNamespaceBehavior = ShutdownNamespaceBehavior.fromId(thingId, pubSubMediator, getSelf());
        this.changeQueueActor = changeQueueActor;

        getContext().setReceiveTimeout(maxIdleTime);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param changeQueueActor reference of the change queue actor.
     * @param maxIdleTime the interval at which is checked, if the corresponding Thing is still actively
     * updated.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator,
            final ActorRef changeQueueActor,
            final java.time.Duration maxIdleTime) {

        return Props.create(ThingUpdater.class, () -> new ThingUpdater(pubSubMediator, changeQueueActor, maxIdleTime));
    }

    @Override
    public Receive createReceive() {
        return shutdownNamespaceBehavior.createReceive()
                .match(ThingEvent.class, this::processThingEvent)
                .match(ThingTag.class, this::processThingTag)
                .match(PolicyReferenceTag.class, this::processPolicyReferenceTag)
                .match(ReceiveTimeout.class, this::stopThisActor)
                .matchAny(m -> {
                    log.warning("Unknown message in 'eventProcessing' behavior: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void stopThisActor(final ReceiveTimeout receiveTimeout) {
        log.debug("stopping ThingUpdater <{}> due to <{}>", thingId, receiveTimeout);
        getSelf().tell(PoisonPill.getInstance(), ActorRef.noSender());
    }

    /**
     * Export the metadata of this updater.
     */
    private Metadata exportMetadata() {
        return Metadata.of(thingId, thingRevision, policyId, policyRevision);
    }

    /**
     * Push metadata of this updater to the queue of thing-changes to be streamed into the persistence.
     */
    private void enqueueMetadata() {
        changeQueueActor.tell(exportMetadata(), getSelf());
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

    private void processPolicyReferenceTag(final PolicyReferenceTag policyReferenceTag) {
        if (log.isDebugEnabled()) {
            log.debug("Received new Policy-Reference-Tag for thing <{}> with revision <{}>,  policy-id <{}> and " +
                            "policy-revision <{}>: <{}>.",
                    new Object[]{thingId, thingRevision, policyId, policyRevision,
                            policyReferenceTag.asIdentifierString()});
        }

        final PolicyTag policyTag = policyReferenceTag.getPolicyTag();
        if (!Objects.equals(policyId, policyTag.getId()) || policyRevision < policyTag.getRevision()) {
            policyId = policyTag.getId();
            policyRevision = policyTag.getRevision();
            enqueueMetadata();
        } else {
            log.debug("Dropping <{}> because my policyId=<{}> and policyRevision=<{}>",
                    policyReferenceTag, policyId, policyRevision);
        }
        acknowledge(policyReferenceTag);
    }

    private void processThingEvent(final ThingEvent thingEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, thingEvent);

        log.debug("Received new thing event for thing id <{}> with revision <{}>.", thingId,
                thingEvent.getRevision());

        // check if the revision is valid (thingEvent.revision = 1 + sequenceNumber)
        if (thingEvent.getRevision() <= thingRevision) {
            log.debug("Dropped thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor.", thingId,
                    thingEvent.getRevision(), thingRevision);
        } else {
            log.debug("Applying thing event <{}>.", thingEvent);
            thingRevision = thingEvent.getRevision();
            enqueueMetadata();
        }
    }

    private String tryToGetThingId() {
        final Charset utf8 = StandardCharsets.UTF_8;
        try {
            return getThingId(utf8);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(MessageFormat.format("Charset <{0}> is unsupported!", utf8.name()), e);
        }
    }

    private String getThingId(final Charset charset) throws UnsupportedEncodingException {
        final String actorName = self().path().name();
        return URLDecoder.decode(actorName, charset.name());
    }

    private void acknowledge(final IdentifiableStreamingMessage message) {
        final ActorRef sender = getSender();
        if (!getContext().system().deadLetters().equals(sender)) {
            getSender().tell(StreamAck.success(message.asIdentifierString()), getSelf());
        }
    }
}
