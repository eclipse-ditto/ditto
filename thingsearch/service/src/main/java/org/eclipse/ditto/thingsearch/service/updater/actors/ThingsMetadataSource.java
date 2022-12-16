/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;

/**
 * Source of metadata streamed from things-service.
 */
final class ThingsMetadataSource {

    private static final ThingId EMPTY_THING_ID = ThingId.of(LowerBound.emptyEntityId(ThingConstants.ENTITY_TYPE));
    private static final String REVISION = "_revision";
    private static final String POLICY_ID = "policyId";
    private static final String MODIFIED = "_modified";

    private static final List<String> SNAPSHOT_FIELDS = List.of(REVISION, POLICY_ID, MODIFIED);

    private final ActorRef pubSubMediator;
    private final int burst;
    private final Duration idleTimeout;

    private ThingsMetadataSource(final ActorRef pubSubMediator, final int burst, final Duration idleTimeout) {
        this.pubSubMediator = pubSubMediator;
        this.burst = burst;
        this.idleTimeout = idleTimeout;
    }

    static ThingsMetadataSource of(final ActorRef pubSubMediator, final int burst, final Duration idleTimeout) {
        return new ThingsMetadataSource(pubSubMediator, burst, idleTimeout);
    }

    /**
     * Start a stream of metadata from Things persistence.
     *
     * @param lowerBound the lower bound thing ID - may come from the bookmark.
     * @param namespaceFilter list of namespaces used to limit the streamed things
     * @return source of metadata streamed from Things via a resume-source.
     */
    Source<Metadata, NotUsed> createSource(final ThingId lowerBound, final List<String> namespaceFilter) {
        return requestStream(lowerBound, namespaceFilter)
                .flatMapConcat(ThingsMetadataSource::getStreamedSnapshots)
                .map(ThingsMetadataSource::toMetadata)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Object getStartStreamCommand(final ThingId lowerBound, final List<String> namespacesFilter) {
        final SudoStreamSnapshots commandWithoutLowerBound =
                SudoStreamSnapshots.of(burst, idleTimeout.toMillis(), SNAPSHOT_FIELDS, DittoHeaders.empty(),
                        ThingConstants.ENTITY_TYPE);
        final SudoStreamSnapshots command =
                lowerBound.equals(EMPTY_THING_ID) ? commandWithoutLowerBound :
                        commandWithoutLowerBound.withLowerBound(lowerBound);
        final SudoStreamSnapshots commandWithNamespaceFilter = command.withNamespacesFilter(namespacesFilter);

        return DistPubSubAccess.publishViaGroup(SudoStreamSnapshots.TYPE, commandWithNamespaceFilter);
    }

    private Source<SourceRef<?>, NotUsed> requestStream(final ThingId lowerBound, final List<String> namespaceFilter) {
        final Object startStreamCommand = getStartStreamCommand(lowerBound, namespaceFilter);

        return Source.completionStage(Patterns.ask(pubSubMediator, startStreamCommand, idleTimeout))
                .flatMapConcat(response -> {
                    if (response instanceof SourceRef<?>) {
                        return Source.single((SourceRef<?>) response);
                    } else {
                        return Source.failed(new ClassCastException("Not a SourceRef: " + response));
                    }
                });
    }

    private static Source<StreamedSnapshot, NotUsed> getStreamedSnapshots(final SourceRef<?> sourceRef) {
        return sourceRef.getSource()
                .map(element -> {
                    if (element instanceof StreamedSnapshot streamedSnapshot) {
                        return streamedSnapshot;
                    } else {
                        throw new ClassCastException("Not a StreamedSnapshot: " + element);
                    }
                });
    }

    private static Optional<Metadata> toMetadata(final StreamedSnapshot streamedSnapshot) {
        try {
            final JsonObject snapshot = streamedSnapshot.getSnapshot();
            final Optional<PolicyId> optionalPolicyId = snapshot.getValue(Thing.JsonFields.POLICY_ID).map(PolicyId::of);
            final ThingId thingId = ThingId.of(streamedSnapshot.getEntityId());
            final long thingRevision = snapshot.getValueOrThrow(Thing.JsonFields.REVISION);
            final Instant modified = snapshot.getValue(Thing.JsonFields.MODIFIED).map(Instant::parse).orElse(null);
            // policy revision is not known from thing snapshot
            final Optional<PolicyTag> policyTag = optionalPolicyId.map(policyId -> PolicyTag.of(policyId, 0L));
            return Optional.of(Metadata.of(thingId, thingRevision, policyTag.orElse(null), Set.of(), modified, null));
        } catch (PolicyIdInvalidException e) {
            return Optional.empty();
        }
    }

}
