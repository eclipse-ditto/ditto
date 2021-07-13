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
package org.eclipse.ditto.policies.service.persistence.actors.announcements;

import static org.eclipse.ditto.policies.service.persistence.actors.announcements.SubjectExpiryState.DELETED;
import static org.eclipse.ditto.policies.service.persistence.actors.announcements.SubjectExpiryState.TO_ACKNOWLEDGE;
import static org.eclipse.ditto.policies.service.persistence.actors.announcements.SubjectExpiryState.TO_ANNOUNCE;
import static org.eclipse.ditto.policies.service.persistence.actors.announcements.SubjectExpiryState.TO_DELETE;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.internal.models.acks.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteExpiredSubject;
import org.eclipse.ditto.protocol.HeaderTranslator;

import akka.NotUsed;
import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import scala.util.Random$;

/**
 * Actor to track subject deletion announcements for 1 expiring or deleted subject.
 */
public final class SubjectExpiryActor extends AbstractFSM<SubjectExpiryState, NotUsed> {

    private static final AckExtractor<PolicyAnnouncement<?>> ACK_EXTRACTOR =
            AckExtractor.of(PolicyAnnouncement::getEntityId, PolicyAnnouncement::getDittoHeaders);

    /**
     * Compensation for timer inaccuracy and scheduling delay.
     */
    private static final Duration ANNOUNCEMENT_WINDOW = Duration.ofMillis(500);

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private static final NotUsed NULL = NotUsed.getInstance();

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final PolicyId policyId;
    private final Subject subject;
    private final Duration gracePeriod;
    private final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub;
    private final AcknowledgementAggregatorActorStarter ackregatorStarter;
    private final DeleteExpiredSubject deleteExpiredSubject;
    private final Duration persistenceTimeout;
    private final ActorRef commandForwarder;

    private Duration nextBackOff = Duration.ofMillis(500);
    private boolean deleted = false;
    private boolean acknowledged = false;
    private Instant deleteAt;

    private SubjectExpiryActor(final PolicyId policyId, final Subject subject, final Duration gracePeriod,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            final AcknowledgementConfig acknowledgementConfig, final ActorRef commandForwarder) {
        this.policyId = policyId;
        this.subject = subject;
        this.gracePeriod = gracePeriod;
        this.policyAnnouncementPub = policyAnnouncementPub;
        deleteAt = subject.getExpiry().map(SubjectExpiry::getTimestamp).orElseGet(Instant::now);
        ackregatorStarter =
                AcknowledgementAggregatorActorStarter.of(getContext(), acknowledgementConfig, HeaderTranslator.empty());
        this.commandForwarder = commandForwarder;
        deleteExpiredSubject =
                DeleteExpiredSubject.of(policyId, subject, DittoHeaders.newBuilder().responseRequired(false).build());
        persistenceTimeout = acknowledgementConfig.getForwarderFallbackTimeout();
    }

    /**
     * Create the Props object for this actor.
     *
     * @param policyId The policy ID.
     * @param subject The subject.
     * @param gracePeriod How long overdue acknowledgements are tolerated.
     * @param policyAnnouncementPub Ditto pubsub API to publish policy announcements.
     * @param acknowledgementConfig The acknowledgement config.
     * @param forwarder Actor to forward policy commands to.
     * @return The Props object.
     */
    public static Props props(final PolicyId policyId, final Subject subject, final Duration gracePeriod,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            final AcknowledgementConfig acknowledgementConfig, final ActorRef forwarder) {

        return Props.create(SubjectExpiryActor.class, policyId, subject, gracePeriod, policyAnnouncementPub,
                acknowledgementConfig, forwarder);
    }

    @Override
    public void preStart() {
        when(TO_ANNOUNCE, toAnnounce());
        when(TO_ACKNOWLEDGE, toAcknowledge());
        when(TO_DELETE, toDelete());
        when(DELETED, persistenceTimeout, afterDeleted());

        whenUnhandled(matchAnyEvent((event, notUsed) -> {
            log.warning("Received unexpected message <{}> in state <{}>", event, stateName());
            return stay();
        }));

        if (subject.getAnnouncement().flatMap(SubjectAnnouncement::getBeforeExpiry).isPresent()) {
            startWith(TO_ANNOUNCE, NULL);
            final Instant now = Instant.now();
            scheduleAnnouncement(now, getAnnouncementInstant().orElse(now));
        } else {
            startWith(TO_DELETE, NULL);
            subject.getExpiry().ifPresent(this::scheduleDeleteExpiredSubject);
        }
        initialize();
    }

    private FSMStateFunctionBuilder<SubjectExpiryState, NotUsed> toAnnounce() {
        return matchEventEquals(Message.SUBJECT_DELETED, this::subjectDeletedInToAnnounce)
                .eventEquals(Message.ANNOUNCE, this::announce);
        // ACKNOWLEDGED/DELETE: impossible
    }

    private FSMStateFunctionBuilder<SubjectExpiryState, NotUsed> toAcknowledge() {
        return matchEvent(Acknowledgements.class, this::onAcknowledgements)
                .event(DittoRuntimeException.class, this::exceptionInToAcknowledge)
                .eventEquals(Message.ACKNOWLEDGED, this::acknowledged)
                .eventEquals(Message.SUBJECT_DELETED, this::subjectDeletedInToAcknowledge);
        // ANNOUNCE: don't care, already announced
    }

    private FSMStateFunctionBuilder<SubjectExpiryState, NotUsed> toDelete() {
        return matchEventEquals(Message.DELETE, this::delete)
                .eventEquals(Message.SUBJECT_DELETED, this::subjectDeletedInToDelete);
        // ACKNOWLEDGED/ANNOUNCE: don't care, already deleted
    }

    private FSMStateFunctionBuilder<SubjectExpiryState, NotUsed> afterDeleted() {
        return matchEventEquals(Message.SUBJECT_DELETED, this::subjectDeletedInDeleted)
                .eventEquals(StateTimeout(), this::timeoutInDeleted);
        // ACKNOWLEDGED/ANNOUNCE/DELETE: don't care, already deleted
    }

    private State<SubjectExpiryState, NotUsed> delete(final Message delete, final NotUsed notUsed) {
        return scheduleDeleteExpiredSubjectIfNeeded();
    }

    private State<SubjectExpiryState, NotUsed> announce(final Message announce, final NotUsed notUsed) {
        cancelTimer(Message.ANNOUNCE.name());
        if (!(deleted && shouldAnnounceWhenDeleted())) {
            final Instant now = Instant.now();
            final Optional<Instant> announcementInstantOpt = getAnnouncementInstant();
            if (announcementInstantOpt.isPresent()) {
                final var announcementInstant = announcementInstantOpt.get();
                if (!announcementInstant.isBefore(now.plus(ANNOUNCEMENT_WINDOW))) {
                    // ANNOUNCE arrived too early due to e.g. limitation in timer
                    scheduleAnnouncement(now, announcementInstant);
                    return stay();
                }
            }
        }
        final ActorRef ackregator = startAckregatorAndPublishAnnouncement();
        if (ackregator == null) {
            // if no acks requested, do not wait for acknowledgements.
            getSelf().tell(Message.ACKNOWLEDGED, ActorRef.noSender());
        }
        return goTo(TO_ACKNOWLEDGE);
    }

    private State<SubjectExpiryState, NotUsed> exceptionInToAcknowledge(final DittoRuntimeException exception,
            final NotUsed notUsed) {
        final var l = log.withCorrelationId(exception);
        l.error(exception, "Got exception waiting for acknowledgements.");
        if (requiresRedelivery(exception.getHttpStatus())) {
            return retryAnnouncementAfterBackOff(l);
        } else {
            log.error("Exception unrecoverable, giving up: <{}>", exception);
            return scheduleDeleteExpiredSubjectIfNeeded();
        }
    }

    private State<SubjectExpiryState, NotUsed> processSubjectDeletedAndCheckForAnnouncement(
            final State<SubjectExpiryState, NotUsed> stateForNoAnnouncement) {
        setDeleteAt();
        if (!acknowledged && shouldAnnounceWhenDeleted()) {
            // announce immediately
            cancelTimer(Message.ANNOUNCE.name());
            getSelf().tell(Message.ANNOUNCE, ActorRef.noSender());
            return goTo(TO_ANNOUNCE);
        } else {
            return stateForNoAnnouncement;
        }
    }

    private void setDeleteAt() {
        if (!deleted) {
            deleted = true;
            deleteAt = Instant.now();
        }
    }

    private State<SubjectExpiryState, NotUsed> subjectDeletedInToAnnounce(final Message subjectDeleted,
            final NotUsed notUsed) {
        return processSubjectDeletedAndCheckForAnnouncement(stay());
    }

    private State<SubjectExpiryState, NotUsed> subjectDeletedInToAcknowledge(final Message subjectDeleted,
            final NotUsed notUsed) {
        setDeleteAt();
        return stay(); // no need to schedule whenDeleted announcements because announcement and backoff already active
    }

    private State<SubjectExpiryState, NotUsed> subjectDeletedInToDelete(final Message subjectDeleted,
            final NotUsed notUsed) {
        return processSubjectDeletedAndCheckForAnnouncement(stop());
    }

    private State<SubjectExpiryState, NotUsed> subjectDeletedInDeleted(final Message subjectDeleted,
            final NotUsed notUsed) {
        return processSubjectDeletedAndCheckForAnnouncement(stop());
    }

    private State<SubjectExpiryState, NotUsed> timeoutInDeleted(final StateTimeout$ timeout,
            final NotUsed notUsed) {

        if (deleted) {
            log.error("Timeout in DELETED with subject already deleted. This should not happen.");
            return stop();
        }
        final boolean shouldAnnounce = shouldAnnounceWhenDeleted();
        final boolean inGracePeriod = isInGracePeriod(Instant.now().plus(nextBackOff));
        if (acknowledged || !shouldAnnounce || !inGracePeriod) {
            log.error("Timeout waiting for persistence, giving up. acknowledged=<{}> shouldAnnounce=<{}> " +
                    "inGracePeriod=<{}>", acknowledged, shouldAnnounce, inGracePeriod);
            return stop();
        } else {
            // retry deletion
            commandForwarder.tell(deleteExpiredSubject, ActorRef.noSender());
            return goTo(DELETED);
        }
    }

    private State<SubjectExpiryState, NotUsed> acknowledged(final Message acknowledged, final NotUsed notUsed) {
        this.acknowledged = true;
        return scheduleDeleteExpiredSubjectIfNeeded();
    }

    private State<SubjectExpiryState, NotUsed> onAcknowledgements(final Acknowledgements acknowledgements,
            final NotUsed notUsed) {
        final var l = log.withCorrelationId(acknowledgements);
        l.debug("onAcknowledgements <{}>", acknowledgements);
        if (shouldRetry(acknowledgements)) {
            return retryAnnouncementAfterBackOff(l);
        } else {
            // acknowledgements are successful
            acknowledged = true;
            return scheduleDeleteExpiredSubjectIfNeeded();
        }
    }

    private State<SubjectExpiryState, NotUsed> retryAnnouncementAfterBackOff(final ThreadSafeDittoLoggingAdapter l) {
        // back off && retry
        final Instant now = Instant.now();
        nextBackOff = increaseBackOff(nextBackOff);
        final Instant announcementInstant = now.plus(nextBackOff);
        if (isInGracePeriod(announcementInstant)) {
            l.debug("Retrying in grace period <{}>", announcementInstant);
            scheduleAnnouncement(now, announcementInstant);
            return goTo(TO_ANNOUNCE);
        } else if (deleted) {
            // subject already deleted; give up
            l.error("Grace period past for deleted subject <{}>. Giving up.", subject);
            return stop();
        } else {
            // outside of grace period; delete
            l.error("Grace period past for subject <{}>. Deleting.", subject);
            commandForwarder.tell(deleteExpiredSubject, ActorRef.noSender());
            return goTo(DELETED);
        }
    }

    private State<SubjectExpiryState, NotUsed> scheduleDeleteExpiredSubjectIfNeeded() {
        if (!deleted) {
            return goTo(subject.getExpiry()
                    .map(this::scheduleDeleteExpiredSubject)
                    .orElseGet(() -> {
                        doDelete();
                        return DELETED;
                    }));
        } else if (acknowledged) {
            // subject already deleted; nothing to announce; already acknowledged
            return stop();
        } else {
            // not acknowledged but deleted
            getSelf().tell(Message.SUBJECT_DELETED, ActorRef.noSender());
            return goTo(DELETED);
        }
    }

    private SubjectExpiryState scheduleDeleteExpiredSubject(final SubjectExpiry expiry) {
        final Instant expiryTimestamp = expiry.getTimestamp();
        final Duration duration = Duration.between(Instant.now(), expiryTimestamp);
        if (duration.isNegative() || duration.isZero()) {
            log.debug("subject expired, deleting: <{}>", subject);
            doDelete();
            return DELETED;
        } else {
            final Duration scheduleDuration = truncateToOneDay(duration.plus(ANNOUNCEMENT_WINDOW));
            log.debug("Scheduling deletion in: <{}> - cutOff=<{}>", scheduleDuration, expiryTimestamp);
            final var delete = Message.DELETE;
            startSingleTimer(delete.name(), delete, scheduleDuration);
            return TO_DELETE;
        }
    }

    private void doDelete() {
        commandForwarder.tell(deleteExpiredSubject, ActorRef.noSender());
        cancelTimer(Message.DELETE.name());
    }

    private boolean shouldAnnounceWhenDeleted() {
        return subject.getAnnouncement().filter(SubjectAnnouncement::isWhenDeleted).isPresent();
    }

    private boolean shouldRetry(final Acknowledgements acks) {
        return acks.stream()
                .map(Acknowledgement::getHttpStatus)
                .anyMatch(SubjectExpiryActor::requiresRedelivery);
    }

    private boolean isInGracePeriod(final Instant announcementInstant) {
        final Instant expiration = subject.getExpiry().map(SubjectExpiry::getTimestamp).orElse(deleteAt);
        return announcementInstant.isBefore(expiration.plus(gracePeriod));
    }

    private void scheduleAnnouncement(final Instant now, final Instant announcementInstant) {
        final Duration duration = Duration.between(now, announcementInstant);
        if (duration.minus(ANNOUNCEMENT_WINDOW).isNegative()) {
            // should announce right away: announcementInstant is within the announcement window
            // this happens for deleted subjects.
            log.debug("scheduleAnnouncement called with now=<{}>, cutOff=<{}>", now, announcementInstant);
            getSelf().tell(Message.ANNOUNCE, ActorRef.noSender());
        } else {
            final Duration scheduleDuration = truncateToOneDay(duration);
            log.debug("Scheduling message for sending announcement in: <{}> - cutOff=<{}>",
                    scheduleDuration, announcementInstant);
            final var announce = Message.ANNOUNCE;
            startSingleTimer(announce.name(), announce, scheduleDuration);
        }
    }

    @Nullable
    private ActorRef startAckregatorAndPublishAnnouncement() {
        if (!acknowledged && subject.getAnnouncement().isPresent()) {
            final PolicyAnnouncement<?> announcement = getAnnouncement();
            final ActorRef ackregator = startAckregator(announcement);
            log.withCorrelationId(announcement).debug("Publishing <{}>", announcement);
            policyAnnouncementPub.publishWithAcks(announcement, ACK_EXTRACTOR, ackregator);
            return ackregator;
        } else {
            // already acknowledged
            return null;
        }
    }

    @Nullable
    private ActorRef startAckregator(final PolicyAnnouncement<?> announcement) {
        // if has requested acks, start it. otherwise return null
        if (announcement.getDittoHeaders().getAcknowledgementRequests().isEmpty()) {
            return null;
        } else {
            return ackregatorStarter.doStart(policyId, announcement.getDittoHeaders(), this::receiveAcknowledgements,
                    Function.identity());
        }
    }

    private PolicyAnnouncement<?> getAnnouncement() {
        return SubjectDeletionAnnouncement.of(policyId, deleteAt, List.of(subject.getId()), getAnnouncementHeaders());
    }

    private DittoHeaders getAnnouncementHeaders() {
        final DittoHeadersBuilder<?, ?> builder = DittoHeaders.newBuilder().randomCorrelationId();
        subject.getAnnouncement().ifPresent(subjectAnnouncement -> {
            builder.acknowledgementRequests(subjectAnnouncement.getRequestedAcksLabels());
            subjectAnnouncement.getRequestedAcksTimeout().ifPresent(builder::timeout);
        });
        return builder.build();
    }

    private void receiveAcknowledgements(final Object response) {
        getSelf().tell(response, ActorRef.noSender());
    }

    private Optional<Instant> getAnnouncementInstant() {
        return subject.getAnnouncement()
                .flatMap(SubjectAnnouncement::getBeforeExpiry)
                .flatMap(beforeExpiry -> subject.getExpiry()
                        .map(expiry -> expiry.getTimestamp().minus(beforeExpiry.getDuration())));
    }

    private Duration truncateToOneDay(final Duration duration) {
        final Duration oneDay = Duration.ofDays(1);
        return duration.compareTo(oneDay) < 0 ? duration : oneDay;
    }

    private static boolean requiresRedelivery(final HttpStatus status) {
        if (HttpStatus.REQUEST_TIMEOUT.equals(status) || HttpStatus.FAILED_DEPENDENCY.equals(status)) {
            return true;
        }
        return status.isServerError();
    }

    private static Duration increaseBackOff(final Duration requestedBackOff) {
        final Duration backOff = MAX_BACKOFF.minus(requestedBackOff).isNegative() ? MAX_BACKOFF : requestedBackOff;
        final double factor = 0.5 + 0.5 * Random$.MODULE$.nextFloat();
        return backOff.plus(Duration.ofMillis((long) (backOff.toMillis() * factor)));
    }

    /**
     * Messages of this actor.
     */
    public enum Message {

        /**
         * Message to receive when the subject is deleted.
         */
        SUBJECT_DELETED,

        /**
         * Message to receive when a subject deletion announcement is acknowledged.
         */
        ACKNOWLEDGED,

        /**
         * Message to receive when it is time to send a policy deletion announcement.
         */
        ANNOUNCE,

        /**
         * Message to receive when it is time to delete the subject.
         */
        DELETE
    }
}
