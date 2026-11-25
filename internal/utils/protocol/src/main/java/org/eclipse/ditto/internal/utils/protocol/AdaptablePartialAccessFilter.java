/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Utility for filtering {@link Adaptable} payloads based on partial access paths and revoked access paths.
 */
final class AdaptablePartialAccessFilter {

    /**
     * Thing identity and audit fields ({@code thingId}, {@code _namespace}, {@code _revision},
     * {@code _modified}, {@code _created}) that must remain visible to any subscriber holding at
     * least partial read access on the thing; if READ on the thing root is revoked for it, only {@code thingId},
     * which is part of the topic anyway. These are field names from the Ditto Protocol
     * (see {@code org.eclipse.ditto.things.model.Thing.JsonFields}) — they are hardcoded here rather than imported to
     * keep the {@code internal/utils/protocol} module free of a dependency on {@code things/model}.
     */
    public static final Set<JsonPointer> ALWAYS_INCLUDED_THING_FIELDS = Set.of(
            JsonPointer.of("thingId"),
            JsonPointer.of("_namespace"),
            JsonPointer.of("_revision"),
            JsonPointer.of("_modified"),
            JsonPointer.of("_created")
    );

    private static final Set<JsonPointer> THING_ID_FIELD = Set.of(JsonPointer.of("thingId"));
    private static final JsonPointer METADATA_POINTER = JsonPointer.of("_metadata");
    private static final Set<TopicPath.Action> PAYLOAD_CHANGING_ACTIONS = EnumSet.of(TopicPath.Action.CREATE,
            TopicPath.Action.MODIFY, TopicPath.Action.MERGE, TopicPath.Action.DELETE, TopicPath.Action.MIGRATE);

    private AdaptablePartialAccessFilter() {
        // No instantiation
    }

    static Adaptable filter(final Adaptable adaptable, final SubscriberAccess access) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (access.isUnrestricted() || !TopicPath.Group.THINGS.equals(topicPath.getGroup())) {
            return adaptable;
        }
        final Payload payload = adaptable.getPayload();
        final PayloadBuilder payloadBuilder = Payload.newBuilder(payload);
        boolean changed = false;
        if (isPayloadFilterable(topicPath)) {
            final JsonPointer eventPath = eventPath(adaptable);
            final Optional<JsonValue> value = payload.getValue();
            if (value.isPresent()) {
                final JsonValue filteredValue = filterPayloadJson(value.get(), eventPath, access);
                if (filteredValue != value.get()) {
                    payloadBuilder.withValue(filteredValue);
                    changed = true;
                }
            }
            final Optional<Metadata> metadata = payload.getMetadata();
            if (metadata.isPresent()) {
                final JsonValue filteredMetadata = filterPayloadJson(metadata.get(), eventPath, access);
                if (filteredMetadata != metadata.get()) {
                    payloadBuilder.withMetadata(filteredMetadata.isObject() && !filteredMetadata.asObject().isEmpty()
                            ? Metadata.newMetadata(filteredMetadata.asObject())
                            : null);
                    changed = true;
                }
            }
        }
        final Optional<JsonObject> extra = payload.getExtra();
        if (extra.isPresent()) {
            final JsonObject filteredExtra = filterThingJson(extra.get(), access);
            if (filteredExtra != extra.get()) {
                payloadBuilder.withExtra(filteredExtra);
                changed = true;
            }
        }
        if (!changed) {
            return adaptable;
        }
        return ProtocolFactory.newAdaptableBuilder(adaptable)
                .withPayload(payloadBuilder.build())
                .build();
    }

    /**
     * Filters thing-rooted JSON, e.g. the extra fields or a whole thing.
     */
    static JsonObject filterThingJson(final JsonObject thingJson, final SubscriberAccess access) {
        if (access.isUnrestricted()) {
            return thingJson;
        }
        if (access.isMalformed()) {
            return JsonFactory.newObject();
        }
        final Set<JsonPointer> keptFields = keptThingFields(access);
        JsonObject result = thingJson;
        final PartialAccessPathResolver.AccessiblePathsResult partial = access.getPartial();
        if (!partial.hasUnrestrictedAccess()) {
            final Set<JsonPointer> readablePaths = new HashSet<>(keptFields);
            readablePaths.addAll(partial.getAccessiblePaths());
            result = JsonPartialAccessFilter.filterJsonByPaths(result, readablePaths);
        }
        if (!access.getRevokedPaths().isEmpty()) {
            final Set<JsonPointer> removedPaths = new HashSet<>(access.getRevokedPaths());
            access.getRevokedPaths().forEach(path -> removedPaths.add(METADATA_POINTER.append(path)));
            result = JsonPartialAccessFilter.removePaths(result, removedPaths, keptFields);
        }
        return result;
    }

    /**
     * @return whether the payload (value and metadata) of signals with this topic is relative to a path of the thing
     * and must be filtered, i.e. thing events and live thing commands which change the thing.
     */
    static boolean isPayloadFilterable(final TopicPath topicPath) {
        if (!TopicPath.Group.THINGS.equals(topicPath.getGroup())) {
            return false;
        }
        if (TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return true;
        }
        return TopicPath.Channel.LIVE.equals(topicPath.getChannel()) &&
                TopicPath.Criterion.COMMANDS.equals(topicPath.getCriterion()) &&
                topicPath.getAction().filter(PAYLOAD_CHANGING_ACTIONS::contains).isPresent();
    }

    /**
     * @return whether the topic is the one of a live thing command, which must never be altered in transit.
     */
    static boolean isLiveCommand(final TopicPath topicPath) {
        return TopicPath.Channel.LIVE.equals(topicPath.getChannel()) &&
                TopicPath.Criterion.COMMANDS.equals(topicPath.getCriterion());
    }

    /**
     * @return whether filtering changed the payload value or metadata, not counting the extra fields.
     */
    static boolean isPayloadChanged(final Adaptable original, final Adaptable filtered) {
        final Payload originalPayload = original.getPayload();
        final Payload filteredPayload = filtered.getPayload();
        return !originalPayload.getValue().equals(filteredPayload.getValue()) ||
                !originalPayload.getMetadata().equals(filteredPayload.getMetadata());
    }

    /**
     * @return the thing fields a subscriber with at least partial READ access always gets.
     */
    static Set<JsonPointer> keptThingFields(final SubscriberAccess access) {
        return access.isRootRevoked() ? THING_ID_FIELD : ALWAYS_INCLUDED_THING_FIELDS;
    }

    static JsonPointer eventPath(final Adaptable adaptable) {
        return JsonPointer.of(adaptable.getPayload().getPath().toString());
    }

    static boolean isPathReadable(final JsonPointer eventPath, final SubscriberAccess access) {
        if (access.isMalformed() ||
                access.getRevokedPaths().stream().anyMatch(revoked -> isDescendantOrSelf(eventPath, revoked))) {
            return false;
        }
        final PartialAccessPathResolver.AccessiblePathsResult partial = access.getPartial();
        if (partial.hasUnrestrictedAccess()) {
            return true;
        }
        return partial.shouldFilter() && partial.getAccessiblePaths().stream()
                .anyMatch(path -> isDescendantOrSelf(eventPath, path) || isDescendantOrSelf(path, eventPath));
    }

    /**
     * Filters a payload value or payload metadata, both relative to {@code eventPath}.
     *
     * @return the filtered JSON, the same instance if nothing was removed.
     */
    private static JsonValue filterPayloadJson(final JsonValue json, final JsonPointer eventPath,
            final SubscriberAccess access) {

        if (access.isMalformed() || !isPathReadable(eventPath, access)) {
            return JsonFactory.newObject();
        }
        final PartialAccessPathResolver.AccessiblePathsResult partial = access.getPartial();
        final boolean wholePathAccessible = partial.hasUnrestrictedAccess() ||
                partial.getAccessiblePaths().stream().anyMatch(path -> isDescendantOrSelf(eventPath, path));
        if (!json.isObject() || json.isNull()) {
            final boolean revokedBelow = access.getRevokedPaths().stream()
                    .anyMatch(revoked -> isDescendantOrSelf(revoked, eventPath));
            return wholePathAccessible && !revokedBelow ? json : JsonFactory.newObject();
        }
        final Set<JsonPointer> keptFields = eventPath.isEmpty() ? keptThingFields(access) : Set.of();
        JsonObject result = json.asObject();
        if (!wholePathAccessible) {
            final Set<JsonPointer> readablePaths = rebaseOnEventPath(eventPath, partial.getAccessiblePaths());
            if (readablePaths.isEmpty()) {
                return JsonFactory.newObject();
            }
            readablePaths.addAll(keptFields);
            result = JsonPartialAccessFilter.filterJsonByPaths(result, readablePaths);
        }
        return JsonPartialAccessFilter.removePaths(result, rebaseOnEventPath(eventPath, access.getRevokedPaths()),
                keptFields);
    }

    /**
     * Re-expresses the thing-absolute {@code paths} relative to {@code eventPath}, keeping only those which can occur
     * at all within a payload rooted at {@code eventPath}.
     */
    private static Set<JsonPointer> rebaseOnEventPath(final JsonPointer eventPath, final Set<JsonPointer> paths) {
        final Set<JsonPointer> rebased = new HashSet<>();
        for (final JsonPointer path : paths) {
            if (isDescendantOrSelf(path, eventPath)) {
                rebased.add(path.getSubPointer(eventPath.getLevelCount()).orElse(JsonPointer.empty()));
            }
        }
        return rebased;
    }

    /**
     * @return whether {@code path} is {@code base} itself or lies below it. Compared level by level rather than by
     * string prefix, so {@code /features/foo} is not treated as lying below {@code /features/fo}. An empty
     * {@code base} (the thing root) is an ancestor of everything.
     */
    private static boolean isDescendantOrSelf(final JsonPointer path, final JsonPointer base) {
        final int baseLevelCount = base.getLevelCount();
        if (path.getLevelCount() < baseLevelCount) {
            return false;
        }
        for (int i = 0; i < baseLevelCount; i++) {
            if (!path.get(i).equals(base.get(i))) {
                return false;
            }
        }
        return true;
    }

}
