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
package org.eclipse.ditto.services.thingsearch.persistence.write.model;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThingResponse;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;

import akka.actor.ActorRef;

/**
 * Data class holding information about a "thingEntities" database record.
 */
public final class Metadata {

    private final ThingId thingId;
    private final long thingRevision;
    @Nullable private final PolicyId policyId;
    @Nullable private final Long policyRevision;
    @Nullable final Instant modified;
    private final List<StartedTimer> timers;
    private final List<ActorRef> senders;
    private final boolean invalidateCache;

    private Metadata(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyId policyId,
            @Nullable final Long policyRevision,
            @Nullable final Instant modified,
            final Collection<StartedTimer> timers,
            final Collection<ActorRef> senders,
            final boolean invalidateCache) {

        this.thingId = thingId;
        this.thingRevision = thingRevision;
        this.policyId = policyId;
        this.policyRevision = policyRevision;
        this.modified = modified;
        this.timers = List.copyOf(timers);
        this.senders = List.copyOf(senders);
        this.invalidateCache = invalidateCache;
    }

    /**
     * Create an Metadata object.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param policyId the Policy ID if the Thing has one.
     * @param policyRevision the Policy revision if the Thing has a policy, or null if it does not.
     * @param timer an optional timer measuring the search updater's consistency lag.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyId policyId,
            @Nullable final Long policyRevision,
            @Nullable final StartedTimer timer) {

        return new Metadata(thingId, thingRevision, policyId, policyRevision, null,
                null != timer ? List.of(timer) : List.of(), List.of(), false);
    }

    /**
     * Create an Metadata object retaining the original sender of an event.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param policyId the Policy ID if the Thing has one.
     * @param policyRevision the Policy revision if the Thing has a policy, or null if it does not.
     * @param timer an optional timer measuring the search updater's consistency lag.
     * @param sender the sender.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyId policyId,
            @Nullable final Long policyRevision,
            @Nullable final StartedTimer timer,
            final ActorRef sender) {

        return new Metadata(thingId, thingRevision, policyId, policyRevision, null,
                null != timer ? List.of(timer) : List.of(), List.of(sender), false);
    }

    /**
     * Create an Metadata object retaining the original senders of an event.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param policyId the Policy ID if the Thing has one.
     * @param policyRevision the Policy revision if the Thing has a policy, or null if it does not.
     * @param modified the timestamp of the last change incorporated into the search index, or null if not known.
     * @param timers the timers measuring the search updater's consistency lag.
     * @param senders the senders.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyId policyId,
            @Nullable final Long policyRevision,
            @Nullable final Instant modified,
            final Collection<StartedTimer> timers,
            final Collection<ActorRef> senders) {

        return new Metadata(thingId, thingRevision, policyId, policyRevision, modified, timers, senders,
                false);
    }

    /**
     * Create an Metadata object with timestamp for the last modification.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param policyId the Policy ID if the Thing has one.
     * @param policyRevision the Policy revision if the Thing has a policy, or null if it does not.
     * @param modified the timestamp of the last change incorporated into the search index, or null if not known.
     * @param timer an optional timer measuring the search updater's consistency lag.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyId policyId,
            @Nullable final Long policyRevision,
            @Nullable final Instant modified,
            @Nullable final StartedTimer timer) {

        return new Metadata(thingId, thingRevision, policyId, policyRevision, modified,
                null != timer ? List.of(timer) : List.of(), List.of(), false);
    }

    /**
     * Recover the metadata from an UpdateThingResponse.
     *
     * @param updateThingResponse the response.
     * @return the metadata.
     */
    public static Metadata fromResponse(final UpdateThingResponse updateThingResponse) {
        return of(updateThingResponse.getThingId(), updateThingResponse.getThingRevision(),
                updateThingResponse.getPolicyId().orElse(null),
                updateThingResponse.getPolicyRevision().orElse(null),
                null);
    }

    /**
     * Create a copy of this metadata requesting cache invalidation.
     *
     * @return the copy.
     */
    public Metadata invalidateCache() {
        return new Metadata(thingId, thingRevision, policyId, policyRevision, modified, timers, senders, true);
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
     * @return the policyId of the Thing according to the search index.
     */
    public Optional<PolicyId> getPolicyId() {
        return Optional.ofNullable(policyId);
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
        return getPolicyId().map(PolicyId::toString).orElse("");
    }

    /**
     * Returns the revision of the Policy according to the search index.
     *
     * @return the revision of the Policy according to the search index.
     */
    public Optional<Long> getPolicyRevision() {
        return Optional.ofNullable(policyRevision);
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
     * Returns the timers measuring the consistency lag.
     *
     * @return the timers.
     */
    public List<StartedTimer> getTimers() {
        return timers;
    }

    /**
     * Return the senders of the originating event which should e.g. receive ACKs.
     *
     * @return the senders.
     */
    public List<ActorRef> getSenders() {
        return senders;
    }

    /**
     * Returns whether an acknowledgement for the successful adding to the search index is requested.
     *
     * @return whether {@code "search-persisted"} is requested.
     */
    public boolean isShouldAcknowledge() {
        return !senders.isEmpty();
    }

    /**
     * Returns whether this metadata should invalidate the enforcer cache.
     *
     * @return whether to invalidate the enforcer cache.
     */
    public boolean shouldInvalidateCache() {
        return invalidateCache;
    }

    /**
     * Prepend new timers and senders to the timers and senders stored in this object.
     *
     * @param newMetadata a previous metadata record.
     * @return the new metadata with concatenated senders.
     */
    public Metadata prependTimersAndSenders(final Metadata newMetadata) {
        final List<StartedTimer> newTimers =
                Stream.concat(newMetadata.timers.stream(), timers.stream()).collect(Collectors.toList());
        final List<ActorRef> newSenders =
                Stream.concat(newMetadata.senders.stream(), senders.stream()).collect(Collectors.toList());
        return new Metadata(newMetadata.thingId, newMetadata.thingRevision, newMetadata.policyId,
                newMetadata.policyRevision, newMetadata.modified, newTimers, newSenders,
                invalidateCache || newMetadata.invalidateCache);
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

    private void send(final Acknowledgement ack) {
        timers.forEach(timer -> {
            if (timer.isRunning()) {
                timer.tag("success", ack.isSuccess()).stop();
            }
        });
        senders.forEach(sender -> sender.tell(ack, ActorRef.noSender()));
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
                Objects.equals(policyRevision, that.policyRevision) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(modified, that.modified) &&
                Objects.equals(timers, that.timers) &&
                Objects.equals(senders, that.senders) &&
                invalidateCache == that.invalidateCache;
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, thingRevision, policyId, policyRevision, modified, timers, senders,
                invalidateCache);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", thingRevision=" + thingRevision +
                ", policyId=" + policyId +
                ", policyRevision=" + policyRevision +
                ", modified=" + modified +
                ", timers=" + timers +
                ", senders=" + senders +
                ", invalidateCache=" + invalidateCache +
                "]";
    }

}
