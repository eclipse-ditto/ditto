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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.Test;

/**
 * Unit tests for {@link LiveEventAccessPaths}.
 */
public final class LiveEventAccessPathsTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "thing");
    private static final DittoHeaders LIVE = DittoHeaders.newBuilder().channel("live").build();
    // device data which the twin does not need to contain
    private static final AttributesModified LIVE_EVENT = AttributesModified.of(THING_ID,
            Attributes.newBuilder().set("pub", "p").set("secret", "s").build(), 1L, Instant.EPOCH, LIVE, null);

    private static final Policy POLICY = Policy.newBuilder(PolicyId.of(THING_ID))
            .forLabel("ALICE").setSubject(subject("alice"))
            .setGrantedPermissions("thing", "/", "READ")
            .forLabel("GROUP_X").setSubject(subject("group-x"))
            .setRevokedPermissions("thing", "/attributes/secret", "READ")
            .forLabel("CAROL").setSubject(subject("carol"))
            .setGrantedPermissions("thing", "/attributes/pub", "READ")
            .build();

    @Test
    public void addsPartialAndRevokedAccessPathsOfThePayload() {
        final ThingEvent<?> result = LiveEventAccessPaths.addTo(LIVE_EVENT, PolicyEnforcer.of(POLICY), true);

        assertThat(header(result, DittoHeaderDefinition.PARTIAL_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:carol\"],\"paths\":{\"attributes/pub\":[0]}}"));
        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}"));
    }

    @Test
    public void addsRevokedAccessPathsAlsoWhenPartialAccessEventsAreDisabled() {
        final ThingEvent<?> result = LiveEventAccessPaths.addTo(LIVE_EVENT, PolicyEnforcer.of(POLICY), false);

        assertThat(result.getDittoHeaders().get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())).isNull();
        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}"));
    }

    @Test
    public void returnsSameEventIfReadIsRestrictedForNobody() {
        final Policy unrestricted = Policy.newBuilder(PolicyId.of(THING_ID))
                .forLabel("ALICE").setSubject(subject("alice"))
                .setGrantedPermissions("thing", "/", "READ")
                .build();

        assertThat(LiveEventAccessPaths.addTo(LIVE_EVENT, PolicyEnforcer.of(unrestricted), true)).isSameAs(LIVE_EVENT);
    }

    @Test
    public void returnsSameEventWithoutPayload() {
        final ThingDeleted thingDeleted = ThingDeleted.of(THING_ID, 1L, Instant.EPOCH, LIVE, null);

        assertThat(LiveEventAccessPaths.addTo(thingDeleted, PolicyEnforcer.of(POLICY), true)).isSameAs(thingDeleted);
    }

    @Test
    public void addsAccessPathsOfTheDeletedPath() {
        final AttributeDeleted deleted =
                AttributeDeleted.of(THING_ID, JsonPointer.of("secret"), 1L, Instant.EPOCH, LIVE, null);

        final ThingEvent<?> result = LiveEventAccessPaths.addTo(deleted, PolicyEnforcer.of(POLICY), true);

        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes\":[0]}}"));
        assertThat(header(result, DittoHeaderDefinition.PARTIAL_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:carol\"],\"paths\":{}}"));
    }

    @Test
    public void listsPartialSubjectWhichMayReadNothingOfThePayload() {
        final AttributesModified secretOnly = AttributesModified.of(THING_ID,
                Attributes.newBuilder().set("secret", "s").build(), 1L, Instant.EPOCH, LIVE, null);

        final ThingEvent<?> result = LiveEventAccessPaths.addTo(secretOnly, PolicyEnforcer.of(POLICY), true);

        assertThat(header(result, DittoHeaderDefinition.PARTIAL_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:carol\"],\"paths\":{}}"));
    }

    private static JsonObject header(final ThingEvent<?> event, final DittoHeaderDefinition definition) {
        return JsonObject.of(event.getDittoHeaders().get(definition.getKey()));
    }

    private static Subject subject(final String name) {
        return Subject.newInstance("test:" + name, SubjectType.GENERATED);
    }

}
