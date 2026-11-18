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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.Test;

/**
 * Unit tests for {@link PartialAccessSignalFilter}.
 */
public final class PartialAccessSignalFilterTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "thing");
    private static final AuthorizationSubject ALICE = AuthorizationSubject.newInstance("test:alice");
    private static final AuthorizationSubject GROUP_X = AuthorizationSubject.newInstance("test:group-x");
    private static final AuthorizationSubject CAROL = AuthorizationSubject.newInstance("test:carol");
    private static final Attributes ATTRIBUTES = Attributes.newBuilder().set("pub", "p").set("secret", "s").build();

    private static final DittoHeaders REVOKED_FOR_GROUP_X = DittoHeaders.newBuilder()
            .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                    "{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}")
            .readGrantedSubjects(List.of(ALICE))
            .build();
    private static final DittoHeaders PARTIAL_FOR_CAROL = DittoHeaders.newBuilder()
            .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                    "{\"subjects\":[\"test:carol\"],\"paths\":{\"attributes/pub\":[0]}}")
            .readGrantedSubjects(List.of(CAROL))
            .build();

    @Test
    public void returnsSameSignalWithoutAccessRestrictions() {
        final AttributesModified event = attributesModified(DittoHeaders.empty());

        assertThat(readable(event, context(ALICE, GROUP_X)))
                .containsSame(event);
    }

    @Test
    public void restrictedEventIsNotReadableWithoutSubscriberContext() {
        final AttributesModified event = attributesModified(REVOKED_FOR_GROUP_X);

        assertThat(readable(event, null)).isEmpty();
        assertThat(readable(event, AuthorizationContext.empty())).isEmpty();
        assertThat(readable(attributesModified(PARTIAL_FOR_CAROL), null)).isEmpty();
    }

    @Test
    public void restrictedLiveCommandIsNotReadableWithoutSubscriberContext() {
        final ModifyAttributes command = ModifyAttributes.of(THING_ID, ATTRIBUTES,
                REVOKED_FOR_GROUP_X.toBuilder().channel("live").build());

        assertThat(readable(command, null)).isEmpty();
    }

    @Test
    public void restrictedSignalWithoutFilterablePayloadIsReadableWithoutSubscriberContext() {
        final ModifyAttributes twinCommand = ModifyAttributes.of(THING_ID, ATTRIBUTES, REVOKED_FOR_GROUP_X);

        assertThat(readable(twinCommand, null)).containsSame(twinCommand);
    }

    @Test
    public void returnsSameSignalForSubscriberWithoutRestrictedSubject() {
        final AttributesModified event = attributesModified(REVOKED_FOR_GROUP_X);

        assertThat(readable(event, context(ALICE))).containsSame(event);
    }

    @Test
    public void removesFieldRevokedForAnotherSubjectAndKeepsHeaders() {
        final AttributesModified event = attributesModified(REVOKED_FOR_GROUP_X);

        final Signal<?> readable = filter(event, context(ALICE, GROUP_X));

        assertThat(readable).isInstanceOf(AttributesModified.class);
        assertThat(((AttributesModified) readable).getModifiedAttributes())
                .isEqualTo(Attributes.newBuilder().set("pub", "p").build());
        assertThat(readable.getDittoHeaders()).isEqualTo(event.getDittoHeaders());
    }

    @Test
    public void keepsOnlyPartiallyReadableFields() {
        final AttributesModified event = attributesModified(PARTIAL_FOR_CAROL);

        final Signal<?> readable = filter(event, context(CAROL));

        assertThat(((AttributesModified) readable).getModifiedAttributes())
                .isEqualTo(Attributes.newBuilder().set("pub", "p").build());
    }

    @Test
    public void eventOnRevokedPathIsNotReadable() {
        final AttributeModified event = AttributeModified.of(THING_ID, JsonPointer.of("secret"), JsonValue.of("s"),
                1L, Instant.EPOCH, REVOKED_FOR_GROUP_X, null);

        assertThat(readable(event, context(ALICE, GROUP_X))).isEmpty();
    }

    @Test
    public void removesFieldFromThingModifiedAndThingMerged() {
        final Thing thing = Thing.newBuilder().setId(THING_ID).setAttributes(ATTRIBUTES).build();
        final ThingModified thingModified =
                ThingModified.of(thing, 1L, Instant.EPOCH, REVOKED_FOR_GROUP_X, null);
        final ThingMerged thingMerged = ThingMerged.of(THING_ID, JsonPointer.empty(),
                JsonObject.newBuilder().set("attributes", ATTRIBUTES.toJson()).build(), 1L, Instant.EPOCH,
                REVOKED_FOR_GROUP_X, null);

        assertThat(filter(thingModified, context(ALICE, GROUP_X)).toJsonString()).doesNotContain("secret");
        assertThat(filter(thingMerged, context(ALICE, GROUP_X)).toJsonString()).doesNotContain("secret");
    }

    @Test
    public void leavesCommandsUntouched() {
        final ModifyAttributes command = ModifyAttributes.of(THING_ID, ATTRIBUTES, REVOKED_FOR_GROUP_X);

        assertThat(readable(command, context(ALICE, GROUP_X)))
                .containsSame(command);
    }

    @Test
    public void deletionOfRevokedPathIsNotReadable() {
        final AttributeDeleted event = AttributeDeleted.of(THING_ID, JsonPointer.of("secret"), 1L, Instant.EPOCH,
                REVOKED_FOR_GROUP_X, null);

        assertThat(readable(event, context(ALICE, GROUP_X))).isEmpty();
        assertThat(readable(event, context(ALICE))).containsSame(event);
    }

    @Test
    public void eventOnPathNotReadableForPartialSubjectIsNotReadable() {
        final AttributeModified event = AttributeModified.of(THING_ID, JsonPointer.of("secret"), JsonValue.of("s"),
                1L, Instant.EPOCH, PARTIAL_FOR_CAROL, null);

        assertThat(readable(event, context(CAROL))).isEmpty();
    }

    private static Optional<Signal<?>> readable(final Signal<?> signal, @Nullable final AuthorizationContext context) {
        return PartialAccessSignalFilter.viewFor(signal, context).map(ReadableSignalView::getSignal);
    }

    private static Signal<?> filter(final Signal<?> signal, final AuthorizationContext context) {
        final Optional<Signal<?>> result = readable(signal, context);
        assertThat(result).isPresent();
        return result.orElseThrow();
    }

    private static AttributesModified attributesModified(final DittoHeaders headers) {
        return AttributesModified.of(THING_ID, ATTRIBUTES, 1L, Instant.EPOCH, headers, null);
    }

    private static AuthorizationContext context(final AuthorizationSubject first,
            final AuthorizationSubject... further) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, first, further);
    }

}
