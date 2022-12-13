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
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;
import org.eclipse.ditto.policies.service.common.config.PolicyAnnouncementConfig;
import org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.SudoDeleteExpiredSubject;

import akka.NotUsed;
import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
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

    private static final NotUsed NULL = NotUsed.getInstance();

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final PolicyId policyId;
    private final Subject subject;
    private final Duration gracePeriod;
    private final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub;
    private final SudoDeleteExpiredSubject sudoDeleteExpiredSubject;
    private final Duration persistenceTimeout;
    private final ActorRef commandForwarder;
    private final boolean enableAnnouncementsWhenDeleted;
    private final Duration defaultRandomizationInterval;


    private final Duration maxBackOff;
    private final double backOffRandomFactor;

    private Duration nextBackOff;
    private boolean deleted;
    private Instant deleteAt;
    private boolean acknowledged;
    private final Address selfRemoteAddress;

    @SuppressWarnings("unused")
    private SubjectExpiryActor(final PolicyId policyId,
            final Subject subject,
            final Duration gracePeriod,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            final Duration maxTimeout,
            final ActorRef commandForwarder,
            final PolicyAnnouncementConfig config) {

        this.policyId = policyId;
        this.subject = subject;
        this.gracePeriod = gracePeriod;
        this.policyAnnouncementPub = policyAnnouncementPub;
        enableAnnouncementsWhenDeleted = config.isEnableAnnouncementsWhenDeleted();
        defaultRandomizationInterval = config.getDefaultRandomizationInterval();

        this.commandForwarder = commandForwarder;
        sudoDeleteExpiredSubject =
                SudoDeleteExpiredSubject.of(policyId, subject,
                        DittoHeaders.newBuilder().responseRequired(false).build());
        persistenceTimeout = maxTimeout;

        final var backOffConfig = config.getExponentialBackOffConfig();
        maxBackOff = backOffConfig.getMax();
        backOffRandomFactor = Math.max(0.0, backOffConfig.getRandomFactor());

        nextBackOff = backOffConfig.getMin();
        deleted = false;
        deleteAt = subject.getExpiry().map(SubjectExpiry::getTimestamp).orElseGet(Instant::now);
        acknowledged = false;
        selfRemoteAddress = Cluster.get(getContext().systemImpl()).selfUniqueAddress().address();
    }

    /**
     * Create the Props object for this actor.
     *
     * @param policyId the policy ID.
     * @param subject the subject.
     * @param gracePeriod how long overdue acknowledgements are tolerated.
     * @param policyAnnouncementPub Ditto pubsub API to publish policy announcements.
     * @param maxTimeout the maximum timeout.
     * @param forwarder actor to forward policy commands to.
     * @param config the policy announcement config.
     * @return The Props object.
     */
    public static Props props(final PolicyId policyId,
            final Subject subject,
            final Duration gracePeriod,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            final Duration maxTimeout,
            final ActorRef forwarder,
            final PolicyAnnouncementConfig config) {

        return Props.create(SubjectExpiryActor.class,
                policyId,
                subject,
                gracePeriod,
                policyAnnouncementPub,
                maxTimeout,
                forwarder,
                config);
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

        onTransition((from, to) -> log.debug("StateTransition <{}> to <{}>", from, to));

        if (subject.getAnnouncement().flatMap(SubjectAnnouncement::getBeforeExpiry).isPresent()) {
            log.debug("Starting in <{}>", TO_ANNOUNCE);
            startWith(TO_ANNOUNCE, NULL);
            final Instant now = Instant.now();
            scheduleAnnouncement(now, getAnnouncementInstant().orElse(now));
        } else {
            log.debug("Starting in <{}>", TO_DELETE);
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
        log.debug("Got DELETE in TO_DELETE");

        return scheduleDeleteExpiredSubjectIfNeeded();
    }

    private State<SubjectExpiryState, NotUsed> announce(final Message announce, final NotUsed notUsed) {
        log.debug("Got ANNOUNCE in TO_ANNOUNCE");
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

        final boolean directlyAckMessage = startAckregatorAndPublishAnnouncement();
        if (directlyAckMessage) {
            // if no acks requested, do not wait for acknowledgements.
            getSelf().tell(Message.ACKNOWLEDGED, ActorRef.noSender());
        }

        return goTo(TO_ACKNOWLEDGE);
    }

    private State<SubjectExpiryState, NotUsed> exceptionInToAcknowledge(final DittoRuntimeException exception,
            final NotUsed notUsed) {
        final var l = log.withCorrelationId(exception);
        l.info("Got exception waiting for acknowledgements: <{}: {}>", exception.getClass().getSimpleName(),
                exception.getMessage());
        if (requiresRedelivery(exception.getHttpStatus())) {
            return retryAnnouncementAfterBackOff(l);
        } else {
            l.warning("Exception unrecoverable, giving up: <{}: {}>", exception.getClass().getSimpleName(),
                    exception.getMessage());

            return scheduleDeleteExpiredSubjectIfNeeded();
        }
    }

    private State<SubjectExpiryState, NotUsed> processSubjectDeletedAndCheckForAnnouncement(
            final State<SubjectExpiryState, NotUsed> stateForNoAnnouncement
    ) {
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
        log.debug("Got SUBJECT_DELETED in TO_ANNOUNCE");
        return processSubjectDeletedAndCheckForAnnouncement(stay());
    }

    private State<SubjectExpiryState, NotUsed> subjectDeletedInToAcknowledge(final Message subjectDeleted,
            final NotUsed notUsed) {
        log.debug("Got SUBJECT_DELETED in TO_ACKNOWLEDGE");
        setDeleteAt();

        return stay(); // no need to schedule whenDeleted announcements because announcement and backoff already active
    }

    private State<SubjectExpiryState, NotUsed> subjectDeletedInToDelete(final Message subjectDeleted,
            final NotUsed notUsed) {

        log.debug("Got SUBJECT_DELETED in TO_DELETE");
        return processSubjectDeletedAndCheckForAnnouncement(stop());
    }

    private State<SubjectExpiryState, NotUsed> subjectDeletedInDeleted(final Message subjectDeleted,
            final NotUsed notUsed) {

        log.debug("Got SUBJECT_DELETED in DELETED");
        return processSubjectDeletedAndCheckForAnnouncement(stop());
    }

    private State<SubjectExpiryState, NotUsed> timeoutInDeleted(final StateTimeout$ timeout, final NotUsed notUsed) {
        if (deleted) {
            log.error("Timeout in DELETED with subject already deleted. This should not happen.");
            return stop();
        }
        log.debug("Timeout in DELETED");
        final boolean shouldAnnounce = shouldAnnounceWhenDeleted();
        final boolean inGracePeriod = isInGracePeriod(Instant.now().plus(nextBackOff));
        if (acknowledged || !shouldAnnounce || !inGracePeriod) {
            log.error("Timeout waiting for ACKs, giving up. acknowledged=<{}> shouldAnnounce=<{}> " +
                    "inGracePeriod=<{}>", acknowledged, shouldAnnounce, inGracePeriod);

            return stop();
        } else {
            // retry deletion
            commandForwarder.tell(sudoDeleteExpiredSubject, ActorRef.noSender());

            return goTo(DELETED);
        }
    }

    private State<SubjectExpiryState, NotUsed> acknowledged(final Message acknowledged, final NotUsed notUsed) {
        log.debug("Got ACKNOWLEDGED in TO_ACKNOWLEDGE");
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

    private State<SubjectExpiryState, NotUsed> retryAnnouncementAfterBackOff(final DittoDiagnosticLoggingAdapter l) {
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
            // log as error as this must not happen without a downtime of the service < gracePeriod
            l.error("Grace period past for deleted subject <{}>. Giving up.", subject);

            return stop();
        } else {
            // outside of grace period; delete
            l.info("Grace period past for subject <{}>. Deleting.", subject);
            commandForwarder.tell(sudoDeleteExpiredSubject, ActorRef.noSender());

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
        commandForwarder.tell(sudoDeleteExpiredSubject, ActorRef.noSender());
        cancelTimer(Message.DELETE.name());
    }

    private boolean shouldAnnounceWhenDeleted() {
        return enableAnnouncementsWhenDeleted &&
                subject.getAnnouncement().filter(SubjectAnnouncement::isWhenDeleted).isPresent();
    }

    private static boolean shouldRetry(final Acknowledgements acks) {
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

    private boolean startAckregatorAndPublishAnnouncement() {
        if (!acknowledged && subject.getAnnouncement().isPresent()) {
            final PolicyAnnouncement<?> announcement = getAnnouncement();
            return startAckregator(announcement);
        } else {
            // already acknowledged
            return true;
        }
    }

    private boolean startAckregator(final PolicyAnnouncement<?> announcement) {
        if (announcement.getDittoHeaders().getAcknowledgementRequests().isEmpty()) {
            return handleSignalWithoutAckregator(announcement);
        } else {
            return handleSignalWithAckregator(announcement);
        }
    }

    private void onAggregatedResponseOrError(final Object responseOrError) {
        getSelf().tell(responseOrError, ActorRef.noSender());
    }

    private boolean handleSignalWithAckregator(final PolicyAnnouncement<?> announcement) {
        final ActorRef aggregatorActor = startAcknowledgementAggregator(announcement);

        log.withCorrelationId(announcement)
                .info("Publishing PolicyAnnouncement with ack requests: <{}>", announcement.getType());
        log.withCorrelationId(announcement)
                .debug("Publishing PolicyAnnouncement with ack requests: <{}>", announcement);

        final String addressSerializationFormat = aggregatorActor.path()
                .toSerializationFormatWithAddress(selfRemoteAddress);
        final PolicyAnnouncement<?> adjustedSignal =
                announcement.setDittoHeaders(announcement.getDittoHeaders().toBuilder()
                        .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), addressSerializationFormat)
                        .build());
        policyAnnouncementPub.publishWithAcks(adjustedSignal, policyId, ACK_EXTRACTOR, aggregatorActor);

        return false;
    }

    private ActorRef startAcknowledgementAggregator(final PolicyAnnouncement<?> announcement) {
        final Duration timeout = announcement.getDittoHeaders().getTimeout().orElse(persistenceTimeout);
        // cap used timeout to max timeout defined by persistenceTimeout
        final Duration useTimeout = timeout.compareTo(persistenceTimeout) > 0 ? persistenceTimeout : timeout;
        final Props props = PolicyAnnouncementAcknowledgementAggregatorActor.props(announcement, useTimeout,
                this::onAggregatedResponseOrError);
        return getContext().actorOf(props);
    }

    private boolean handleSignalWithoutAckregator(final Signal<?> signalWithoutAckregator) {
        log.withCorrelationId(signalWithoutAckregator)
                .info("Publishing PolicyAnnouncement: <{}>", signalWithoutAckregator.getType());
        log.withCorrelationId(signalWithoutAckregator)
                .debug("Publishing PolicyAnnouncement: <{}>", signalWithoutAckregator);
        policyAnnouncementPub.publishWithAcks((PolicyAnnouncement<?>) signalWithoutAckregator, policyId, ACK_EXTRACTOR,
                ActorRef.noSender());
        return true;
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

    /**
     * Gets the instant when the announcement should be sent (instant of expiry - time before expiry that the
     * announcement should be sent)
     */
    private Optional<Instant> getAnnouncementInstant() {
        return subject.getAnnouncement()
                .flatMap(SubjectAnnouncement::getBeforeExpiry)
                .flatMap(beforeExpiry -> subject.getExpiry()
                        .map(expiry -> expiry.getTimestamp().minus(beforeExpiry.getDuration())))
                .flatMap(this::subtractRandomFactor);
    }

    /**
     * subtract a random factor from the announcement instant in effort to prevent policy announcement bulks, which
     * could cause to dropped messages in connections not consuming fast enough.
     */
    private Optional<Instant> subtractRandomFactor(final Instant announcementInstant) {
        return subject.getAnnouncement()
                .map(a -> a.getRandomizationInterval().orElse(DittoDuration.of(defaultRandomizationInterval)))
                .map(interval -> announcementInstant.minusMillis(getRandomMillis(interval)));
    }

    private long getRandomMillis(final DittoDuration interval) {
        final long intervalMillis = interval.getDuration().toMillis();
        return intervalMillis > 0 ? ThreadLocalRandom.current().nextLong(intervalMillis) : intervalMillis;
    }

    private static Duration truncateToOneDay(final Duration duration) {
        final Duration oneDay = Duration.ofDays(1);

        return duration.compareTo(oneDay) < 0 ? duration : oneDay;
    }

    private static boolean requiresRedelivery(final HttpStatus status) {
        if (HttpStatus.REQUEST_TIMEOUT.equals(status) || HttpStatus.FAILED_DEPENDENCY.equals(status)) {
            return true;
        }

        return status.isServerError();
    }

    private Duration increaseBackOff(final Duration requestedBackOff) {
        final Duration backOff = maxBackOff.minus(requestedBackOff).isNegative() ? maxBackOff : requestedBackOff;
        final double factor = 0.5 + 0.5 * backOffRandomFactor * Random$.MODULE$.nextFloat();
        final Duration result = backOff.plus(Duration.ofMillis((long) (backOff.toMillis() * factor)));
        if (result.isNegative() || result.minus(requestedBackOff).isNegative() ||
                maxBackOff.minus(result).isNegative()) {
            // use maxBackOff if next backoff exceeds it or overflows
            return maxBackOff;
        } else {
            return result;
        }
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
