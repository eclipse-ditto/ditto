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
package org.eclipse.ditto.things.service.utils;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * Adds the READ restrictions of subscribers to live signals, which unlike twin events are not enriched by the
 * persistence actor. They are calculated on the payload of the signal only, as the Thing need not contain it. The
 * paths are not collapsed to ancestors within the payload, as subscribers also apply them to Thing-rooted
 * {@code extra} fields.
 *
 * @since 3.9.9
 */
@ThreadSafe
public final class LiveSignalAccessPaths {

    private static final int MEMO_SIZE = 4;

    private final boolean partialAccessEventsEnabled;
    private final AccessRestrictionsMemo memo;

    /**
     * @param partialAccessEventsEnabled whether subjects with partial READ permissions receive live events.
     */
    public LiveSignalAccessPaths(final boolean partialAccessEventsEnabled) {
        this.partialAccessEventsEnabled = partialAccessEventsEnabled;
        memo = new AccessRestrictionsMemo(MEMO_SIZE);
    }

    /**
     * Adds the {@code ditto-partial-access-paths} and {@code ditto-revoked-access-paths} headers to a live event and
     * removes the subjects with partial READ permissions which can read nothing of its payload from its
     * {@code ditto-read-subjects}.
     *
     * @param liveEvent the authorized live event.
     * @param policyEnforcer the policy enforcer of the Thing, filtered for the Thing's namespace.
     * @return the live event with the restrictions, or the live event itself if READ is restricted for nobody.
     */
    public ThingEvent<?> addTo(final ThingEvent<?> liveEvent, final PolicyEnforcer policyEnforcer) {
        final JsonPointer resourcePath = liveEvent.getResourcePath();
        // deletions carry no entity; their path must still be checked
        final Optional<JsonValue> payload = liveEvent.getEntity()
                .or(() -> resourcePath.isEmpty() ? Optional.empty() : Optional.of(JsonValue.nullLiteral()));
        return restrictionsOf(resourcePath, payload.orElse(null), policyEnforcer, partialAccessEventsEnabled)
                .applyTo(liveEvent);
    }

    /**
     * Adds the {@code ditto-revoked-access-paths} header to a live command carrying a payload or deleting a path.
     *
     * @param liveCommand the authorized live command.
     * @param policyEnforcer the policy enforcer of the Thing, filtered for the Thing's namespace.
     * @return the live command with the header, or the live command itself if READ is revoked for nobody in it.
     */
    public ThingCommand<?> addTo(final ThingCommand<?> liveCommand, final PolicyEnforcer policyEnforcer) {
        if (!(liveCommand instanceof ThingModifyCommand<?> modifyCommand)) {
            return liveCommand;
        }
        final JsonPointer resourcePath = modifyCommand.getResourcePath();
        final Optional<JsonValue> payload = modifyCommand.getEntity()
                .or(() -> resourcePath.isEmpty() || modifyCommand.getCategory() != Command.Category.DELETE
                        ? Optional.empty()
                        : Optional.of(JsonValue.nullLiteral()));
        return restrictionsOf(resourcePath, payload.orElse(null), policyEnforcer, false).applyTo(liveCommand);
    }

    private AccessRestrictions restrictionsOf(final JsonPointer resourcePath, @Nullable final JsonValue payload,
            final PolicyEnforcer policyEnforcer, final boolean withPartialAccessPaths) {

        if (payload == null || !AccessRestrictions.mayBeRestricted(policyEnforcer, withPartialAccessPaths)) {
            return AccessRestrictions.NONE;
        }
        final JsonObject payloadAsThing = toThingJson(resourcePath, payload);
        if (payloadAsThing == null || payloadAsThing.isEmpty()) {
            return AccessRestrictions.NONE;
        }
        return memo.get(policyEnforcer, PartialAccessPathCalculator.structureHash(payloadAsThing),
                withPartialAccessPaths,
                () -> AccessRestrictions.calculate(policyEnforcer, payloadAsThing, withPartialAccessPaths, false));
    }

    @Nullable
    private static JsonObject toThingJson(final JsonPointer resourcePath, final JsonValue value) {
        if (resourcePath.isEmpty()) {
            return value.isObject() && !value.isNull() ? value.asObject() : null;
        }
        return JsonObject.empty().setValue(resourcePath, value);
    }

}
