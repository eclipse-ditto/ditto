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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.UpdateReason;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;

/**
 * Data class holding information about a "thingEntities" database record.
 */
public final class Metadata {

    private final ThingId thingId;
    private final long thingRevision;
    @Nullable private final PolicyTag thingPolicy;
    private final Set<PolicyTag> allReferencedPolicies;
    @Nullable private final Instant modified;
    private final List<ThingEvent<?>> events;
    private final List<StartedTimer> timers;
    private final List<ActorSelection> ackRecipients;
    private final boolean invalidateThing;
    private final boolean invalidatePolicy;
    private final List<UpdateReason> updateReasons;

    private Metadata(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyTag thingPolicy,
            final Collection<PolicyTag> allReferencedPolicies,
            @Nullable final Instant modified,
            final List<ThingEvent<?>> events,
            final Collection<StartedTimer> timers,
            final Collection<ActorSelection> ackRecipients,
            final boolean invalidateThing,
            final boolean invalidatePolicy,
            final Collection<UpdateReason> updateReasons) {

        this.thingId = thingId;
        this.thingRevision = thingRevision;
        this.thingPolicy = thingPolicy;
        final HashSet<PolicyTag> policyTags = new HashSet<>(allReferencedPolicies);
        if (thingPolicy != null) {
            policyTags.add(thingPolicy);
        }
        this.allReferencedPolicies = Set.copyOf(policyTags);
        this.modified = modified;
        this.events = events;
        this.timers = List.copyOf(timers);
        this.ackRecipients = List.copyOf(ackRecipients);
        this.invalidateThing = invalidateThing;
        this.invalidatePolicy = invalidatePolicy;
        this.updateReasons = List.copyOf(updateReasons);
    }

    /**
     * Create an Metadata object.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param thingPolicy the policy directly referenced by the thing.
     * @param allReferencedPolicies the policy directly and indirectly (via policy import) referenced by the thing.
     * @param timer an optional timer measuring the search updater's consistency lag.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyTag thingPolicy,
            final Collection<PolicyTag> allReferencedPolicies,
            @Nullable final StartedTimer timer) {

        return new Metadata(thingId, thingRevision, thingPolicy, allReferencedPolicies, null,
                List.of(), null != timer ? List.of(timer) : List.of(), List.of(), false, false,
                List.of(UpdateReason.UNKNOWN));
    }

    /**
     * Create a Metadata object retaining the original ackRecipient of an event.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param thingPolicy the policy directly referenced by the thing.
     * @param allReferencedPolicies the policy directly and indirectly (via policy import) referenced by the thing.
     * @param timer an optional timer measuring the search updater's consistency lag.
     * @param ackRecipient the ackRecipient.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyTag thingPolicy,
            final Collection<PolicyTag> allReferencedPolicies,
            final List<ThingEvent<?>> events,
            @Nullable final StartedTimer timer,
            @Nullable final ActorSelection ackRecipient) {

        return new Metadata(thingId, thingRevision, thingPolicy, allReferencedPolicies, null, events,
                null != timer ? List.of(timer) : List.of(),
                null != ackRecipient ? List.of(ackRecipient) : List.of(), false, false, List.of(UpdateReason.UNKNOWN));
    }

    /**
     * Create a Metadata object retaining the original ackRecipient of an event.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param thingPolicy the policy directly referenced by the thing.
     * @param allReferencedPolicies the policy directly and indirectly (via policy import) referenced by the thing.
     * @param modified the timestamp of the last change incorporated into the search index, or null if not known.
     * @param events the events included in the metadata causing the search update.
     * @param timers the timers measuring the search updater's consistency lag.
     * @param ackRecipient the ackRecipient.
     * @param updateReasons the update reasons.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyTag thingPolicy,
            final Collection<PolicyTag> allReferencedPolicies,
            @Nullable final Instant modified,
            final List<ThingEvent<?>> events,
            final Collection<StartedTimer> timers,
            final Collection<ActorSelection> ackRecipient,
            final Collection<UpdateReason> updateReasons) {

        return new Metadata(thingId, thingRevision, thingPolicy, allReferencedPolicies, modified, events, timers,
                ackRecipient, false, false, updateReasons);
    }

    /**
     * Create a Metadata object with timestamp for the last modification.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param thingPolicy the policy directly referenced by the thing.
     * @param allReferencedPolicies the policy directly and indirectly (via policy import) referenced by the thing.
     * @param modified the timestamp of the last change incorporated into the search index, or null if not known.
     * @param timer an optional timer measuring the search updater's consistency lag.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyTag thingPolicy,
            final Collection<PolicyTag> allReferencedPolicies,
            @Nullable final Instant modified,
            @Nullable final StartedTimer timer) {

        return new Metadata(thingId, thingRevision, thingPolicy, allReferencedPolicies, modified,
                List.of(), null != timer ? List.of(timer) : List.of(), List.of(), false, false,
                List.of(UpdateReason.UNKNOWN));
    }

    /**
     * Return a Metadata object for a deleted Thing.
     *
     * @param thingId the ID of the deleted thing.
     * @return the Metadata object.
     */
    public static Metadata ofDeleted(final ThingId thingId) {
        return Metadata.of(thingId, -1, null, Set.of(), null);
    }

    /**
     * Create a copy of this object containing only the IDs and revisions of the thing and policy.
     *
     * @return the exported metadata.
     */
    public Metadata export() {
        return Metadata.of(thingId, thingRevision, thingPolicy, allReferencedPolicies, null);
    }

    /**
     * Create a copy of this metadata requesting cache invalidation.
     *
     * @param invalidateThing whether to invalidate the cached thing.
     * @param invalidatePolicy whether to invalidate the cached policy enforcer.
     * @return the copy.
     */
    public Metadata invalidateCaches(final boolean invalidateThing, final boolean invalidatePolicy) {
        return new Metadata(thingId, thingRevision, thingPolicy, allReferencedPolicies, modified, events, timers,
                ackRecipients, invalidateThing, invalidatePolicy, updateReasons);
    }

    /**
     * Create a copy of this metadata with senders replaced by the argument.
     *
     * @return the copy.
     */
    public Metadata withAckRecipient(final ActorSelection ackRecipient) {
        return new Metadata(thingId, thingRevision, thingPolicy, allReferencedPolicies, modified, events, timers,
                List.of(ackRecipient),
                invalidateThing, invalidatePolicy, updateReasons);
    }

    /**
     * Create a copy of this metadata with senders replaced by the argument.
     *
     * @return the copy.
     */
    public Metadata withUpdateReason(final UpdateReason reason) {
        return new Metadata(thingId, thingRevision, thingPolicy, allReferencedPolicies, modified, events, timers,
                ackRecipients, invalidateThing, invalidatePolicy, List.of(reason));
    }

    /**
     * @return the Thing ID.
     */
    public ThingId getThingId() {
        return thingId;
    }

    /**
     * Returns the revision of the Thing according to the search index.
     *
     * @return the revision of the Thing according to the search index.
     */
    public long getThingRevision() {
        return thingRevision;
    }

    /**
     * @return namespace field as to be written in the persistence.
     */
    public String getNamespaceInPersistence() {
        return thingId.getNamespace();
    }

    /**
     * @return the policyId-field as to be written in the persistence.
     */
    public String getPolicyIdInPersistence() {
        return getThingPolicyTag()
                .map(AbstractEntityIdWithRevision::getEntityId)
                .map(PolicyId::toString)
                .orElse("");
    }

    /**
     * Returns the thing policy tag according to the search index.
     *
     * @return the ID and revision of the Policy according to the search index.
     */
    public Optional<PolicyTag> getThingPolicyTag() {
        return Optional.ofNullable(thingPolicy);
    }

    /**
     * @return all referenced policy tags. This includes the thing policy as well as all policies imported by the
     * thing policy.
     */
    public Set<PolicyTag> getAllReferencedPolicyTags() {
        return allReferencedPolicies;
    }

    /**
     * Returns the timestamp of the last change if any exists.
     *
     * @return the optional timestamp.
     */
    public Optional<Instant> getModified() {
        return Optional.ofNullable(modified);
    }

    /**
     * Returns the known thing events.
     *
     * @return the known thing events.
     */
    public List<ThingEvent<?>> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * Returns the correlationIds of the known thing events.
     *
     * @return the correlation ids.
     */
    public List<String> getEventsCorrelationIds() {
        return events.stream()
                .map(Event::getDittoHeaders)
                .map(DittoHeaders::getCorrelationId)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Returns the timers measuring the consistency lag.
     *
     * @return the timers.
     */
    public List<StartedTimer> getTimers() {
        return timers;
    }

    /**
     * Return selections to receive ACKs.
     *
     * @return the senders.
     */
    public List<ActorSelection> getAckRecipients() {
        return ackRecipients;
    }

    /**
     * Return the reason of the update.
     *
     * @return the update reason.
     */
    public List<UpdateReason> getUpdateReasons() {
        return updateReasons;
    }

    /**
     * Returns whether an acknowledgement for the successful adding to the search index is requested.
     *
     * @return whether {@code "search-persisted"} is requested.
     */
    public boolean isShouldAcknowledge() {
        return !ackRecipients.isEmpty();
    }

    /**
     * Returns whether this metadata should invalidate the cached thing.
     *
     * @return whether to invalidate the cached thing.
     */
    public boolean shouldInvalidateThing() {
        return invalidateThing;
    }

    /**
     * Returns whether this metadata should invalidate the cached policy.
     *
     * @return whether to invalidate the cached policy.
     */
    public boolean shouldInvalidatePolicy() {
        return invalidatePolicy;
    }

    /**
     * Prepend new timers and senders to the timers and senders stored in this object.
     *
     * @param newMetadata a previous metadata record.
     * @return the new metadata with concatenated senders.
     */
    public Metadata append(final Metadata newMetadata) {
        final List<ThingEvent<?>> newEvents = Stream.concat(events.stream(), newMetadata.events.stream()).toList();
        final List<StartedTimer> newTimers = Stream.concat(timers.stream(), newMetadata.timers.stream()).toList();
        final List<ActorSelection> newAckRecipients =
                Stream.concat(ackRecipients.stream(), newMetadata.ackRecipients.stream()).toList();
        final List<UpdateReason> newReasons = Stream.concat(updateReasons.stream(), newMetadata.updateReasons.stream())
                .toList();
        return new Metadata(newMetadata.thingId, newMetadata.thingRevision, newMetadata.thingPolicy,
                newMetadata.allReferencedPolicies, newMetadata.modified, newEvents, newTimers, newAckRecipients,
                invalidateThing || newMetadata.invalidateThing,
                invalidatePolicy || newMetadata.invalidatePolicy,
                newReasons);
    }

    /**
     * Send negative acknowledgements to senders.
     */
    public void sendNAck() {
        send(Acknowledgement.of(DittoAcknowledgementLabel.SEARCH_PERSISTED, thingId,
                HttpStatus.INTERNAL_SERVER_ERROR, DittoHeaders.empty()));
    }

    /**
     * Send positive acknowledgements to senders.
     */
    public void sendAck() {
        send(Acknowledgement.of(DittoAcknowledgementLabel.SEARCH_PERSISTED, thingId, HttpStatus.NO_CONTENT,
                DittoHeaders.empty()));
    }

    /**
     * Send weak acknowledgement to senders.
     *
     * @param payload the payload of the weak acknowledgement.
     */
    public void sendWeakAck(@Nullable final JsonValue payload) {
        send(Acknowledgement.weak(DittoAcknowledgementLabel.SEARCH_PERSISTED, thingId, DittoHeaders.empty(), payload));
    }

    private void send(final Acknowledgement ack) {
        timers.forEach(timer -> {
            if (timer.isRunning()) {
                timer.tag("success", ack.isSuccess()).stop();
            }
        });
        ackRecipients.forEach(ackRecipient -> ackRecipient.tell(ack, ActorRef.noSender()));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Metadata that = (Metadata) o;
        return thingRevision == that.thingRevision &&
                Objects.equals(thingPolicy, that.thingPolicy) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(allReferencedPolicies, that.allReferencedPolicies) &&
                Objects.equals(modified, that.modified) &&
                Objects.equals(events, that.events) &&
                Objects.equals(timers, that.timers) &&
                Objects.equals(ackRecipients, that.ackRecipients) &&
                invalidateThing == that.invalidateThing &&
                invalidatePolicy == that.invalidatePolicy &&
                Objects.equals(updateReasons, that.updateReasons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, thingRevision, thingPolicy, allReferencedPolicies, modified, events, timers,
                ackRecipients, invalidateThing, invalidatePolicy, updateReasons);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", thingRevision=" + thingRevision +
                ", thingPolicy=" + thingPolicy +
                ", allReferencedPolicies=" + allReferencedPolicies +
                ", modified=" + modified +
                ", events=" + events +
                ", timers=[" + timers.size() + " timers]" +
                ", ackRecipients=" + ackRecipients +
                ", invalidateThing=" + invalidateThing +
                ", invalidatePolicy=" + invalidatePolicy +
                ", updateReasons=" + updateReasons +
                "]";
    }

}
