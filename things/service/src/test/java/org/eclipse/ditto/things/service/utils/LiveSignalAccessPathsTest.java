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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.Test;

/**
 * Unit tests for {@link LiveSignalAccessPaths}.
 */
public final class LiveSignalAccessPathsTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "thing");
    private static final List<AuthorizationSubject> READ_GRANTED = List.of(
            AuthorizationSubject.newInstance("test:alice"),
            AuthorizationSubject.newInstance("test:carol"),
            AuthorizationSubject.newInstance("test:group-x"));
    private static final DittoHeaders LIVE = DittoHeaders.newBuilder()
            .channel("live")
            .readGrantedSubjects(READ_GRANTED)
            .build();
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
        final ThingEvent<?> result = new LiveSignalAccessPaths(true).addTo(LIVE_EVENT, PolicyEnforcer.of(POLICY));

        assertThat(header(result, DittoHeaderDefinition.PARTIAL_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:carol\"],\"paths\":{\"attributes/pub\":[0]}}"));
        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}"));
        assertThat(result.getDittoHeaders().getReadGrantedSubjects()).containsExactlyInAnyOrderElementsOf(READ_GRANTED);
    }

    @Test
    public void doesNotCollapsePathsToAncestorsWithinThePayload() {
        final AttributeModified pubOnly =
                AttributeModified.of(THING_ID, JsonPointer.of("pub"), JsonValue.of(1), 1L, Instant.EPOCH, LIVE, null);
        final AttributeModified secretOnly =
                AttributeModified.of(THING_ID, JsonPointer.of("secret"), JsonValue.of(1), 1L, Instant.EPOCH, LIVE,
                        null);
        final LiveSignalAccessPaths underTest = new LiveSignalAccessPaths(true);

        assertThat(header(underTest.addTo(pubOnly, PolicyEnforcer.of(POLICY)),
                DittoHeaderDefinition.PARTIAL_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:carol\"],\"paths\":{\"attributes/pub\":[0]}}"));
        assertThat(header(underTest.addTo(secretOnly, PolicyEnforcer.of(POLICY)),
                DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}"));
    }

    @Test
    public void addsRevokedAccessPathsAlsoWhenPartialAccessEventsAreDisabled() {
        final ThingEvent<?> result = new LiveSignalAccessPaths(false).addTo(LIVE_EVENT, PolicyEnforcer.of(POLICY));

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

        assertThat(new LiveSignalAccessPaths(true).addTo(LIVE_EVENT, PolicyEnforcer.of(unrestricted)))
                .isSameAs(LIVE_EVENT);
    }

    @Test
    public void returnsSameEventWithoutPayload() {
        final ThingDeleted thingDeleted = ThingDeleted.of(THING_ID, 1L, Instant.EPOCH, LIVE, null);

        assertThat(new LiveSignalAccessPaths(true).addTo(thingDeleted, PolicyEnforcer.of(POLICY)))
                .isSameAs(thingDeleted);
    }

    @Test
    public void addsAccessPathsOfTheDeletedPath() {
        final AttributeDeleted deleted =
                AttributeDeleted.of(THING_ID, JsonPointer.of("secret"), 1L, Instant.EPOCH, LIVE, null);

        final ThingEvent<?> result = new LiveSignalAccessPaths(true).addTo(deleted, PolicyEnforcer.of(POLICY));

        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}"));
        assertThat(result.getDittoHeaders().get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())).isNull();
        assertThat(result.getDittoHeaders().getReadGrantedSubjects())
                .doesNotContain(AuthorizationSubject.newInstance("test:carol"));
    }

    @Test
    public void removesPartialSubjectWhichMayReadNothingOfThePayloadFromReadGrantedSubjects() {
        final AttributesModified secretOnly = AttributesModified.of(THING_ID,
                Attributes.newBuilder().set("secret", "s").build(), 1L, Instant.EPOCH, LIVE, null);

        final ThingEvent<?> result = new LiveSignalAccessPaths(true).addTo(secretOnly, PolicyEnforcer.of(POLICY));

        assertThat(result.getDittoHeaders().get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())).isNull();
        assertThat(result.getDittoHeaders().getReadGrantedSubjects()).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance("test:alice"), AuthorizationSubject.newInstance("test:group-x"));
    }

    @Test
    public void marksSubjectsWithRevokedThingRoot() {
        final Policy policy = POLICY.toBuilder()
                .forLabel("GROUP_R").setSubject(subject("group-r"))
                .setRevokedPermissions("thing", "/", "READ")
                .build();

        final ThingEvent<?> result = new LiveSignalAccessPaths(false).addTo(LIVE_EVENT, PolicyEnforcer.of(policy));

        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS)).isEqualTo(JsonObject.of("""
                {"subjects":["test:group-r","test:group-x"],
                 "paths":{"attributes/pub":[0],"attributes/secret":[0,1]},
                 "root":[0]}"""));
    }

    @Test
    public void addsRevokedAccessPathsOfThePayloadToLiveCommands() {
        final ModifyAttributes command = ModifyAttributes.of(THING_ID,
                Attributes.newBuilder().set("pub", "p").set("secret", "s").build(), LIVE);

        final ThingCommand<?> result = new LiveSignalAccessPaths(true).addTo(command, PolicyEnforcer.of(POLICY));

        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}"));
        assertThat(result.getDittoHeaders().get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())).isNull();
        assertThat(result.getDittoHeaders().getReadGrantedSubjects()).containsExactlyInAnyOrderElementsOf(READ_GRANTED);
        assertThat(((WithOptionalEntity<?>) result).getEntity()).isEqualTo(command.getEntity());
    }

    @Test
    public void addsRevokedAccessPathsOfTheDeletedPathToLiveCommands() {
        final DeleteAttribute command = DeleteAttribute.of(THING_ID, JsonPointer.of("secret"), LIVE);

        final ThingCommand<?> result = new LiveSignalAccessPaths(true).addTo(command, PolicyEnforcer.of(POLICY));

        assertThat(header(result, DittoHeaderDefinition.REVOKED_ACCESS_PATHS))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}"));
    }

    @Test
    public void returnsSameLiveCommandWithoutPayload() {
        final RetrieveAttribute command = RetrieveAttribute.of(THING_ID, JsonPointer.of("secret"), LIVE);

        assertThat(new LiveSignalAccessPaths(true).addTo(command, PolicyEnforcer.of(POLICY))).isSameAs(command);
    }

    @Test
    public void reusesRestrictionsForPayloadsOfTheSameStructure() {
        final PolicyEnforcer policyEnforcer = spy(PolicyEnforcer.of(POLICY));
        final LiveSignalAccessPaths underTest = new LiveSignalAccessPaths(false);
        final AttributesModified otherValues = AttributesModified.of(THING_ID,
                Attributes.newBuilder().set("pub", 1).set("secret", 2).build(), 2L, Instant.EPOCH, LIVE, null);

        final ThingEvent<?> first = underTest.addTo(LIVE_EVENT, policyEnforcer);
        final ThingEvent<?> second = underTest.addTo(otherValues, policyEnforcer);

        verify(policyEnforcer, times(1)).getEnforcer();
        assertThat(second.getDittoHeaders().get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()))
                .isEqualTo(first.getDittoHeaders().get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()));
    }

    private static JsonObject header(final org.eclipse.ditto.base.model.headers.WithDittoHeaders signal,
            final DittoHeaderDefinition definition) {
        return JsonObject.of(signal.getDittoHeaders().get(definition.getKey()));
    }

    private static Subject subject(final String name) {
        return Subject.newInstance("test:" + name, SubjectType.GENERATED);
    }

}
