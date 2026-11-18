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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Tests the removal of revoked paths by {@link AdaptablePartialAccessFilter} for subscribers whose authorization
 * context contains a subject granted READ and a subject with READ revoked.
 */
public final class AdaptableRevokedAccessFilterTest {

    private static final AuthorizationSubject ALICE = AuthorizationSubject.newInstance("test:alice");
    private static final AuthorizationSubject GROUP_X = AuthorizationSubject.newInstance("test:group-x");
    private static final AuthorizationSubject PARTIAL = AuthorizationSubject.newInstance("test:partial");
    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "thing");

    private static final JsonObject ATTRIBUTES = JsonObject.newBuilder()
            .set("pub", "p")
            .set("secret", "s")
            .build();
    private static final JsonObject THING = JsonObject.newBuilder()
            .set("thingId", THING_ID.toString())
            .set("policyId", THING_ID.toString())
            .set("attributes", ATTRIBUTES)
            .build();

    @Test
    public void removesFieldRevokedForAnotherSubjectFromThingRootedEvent() {
        final Adaptable adaptable = thingEvent(JsonPointer.empty(), THING, null,
                headers(Map.of("attributes/secret", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE, GROUP_X));

        assertThat(payloadValue(filtered)).isEqualTo(JsonObject.newBuilder()
                .set("thingId", THING_ID.toString())
                .set("policyId", THING_ID.toString())
                .set("attributes", JsonObject.newBuilder().set("pub", "p").build())
                .build());
    }

    @Test
    public void removesRevokedFieldFromEventBelowThingRoot() {
        final Adaptable adaptable = thingEvent(JsonPointer.of("/attributes"), ATTRIBUTES, null,
                headers(Map.of("attributes/secret", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE, GROUP_X));

        assertThat(payloadValue(filtered)).isEqualTo(JsonObject.newBuilder().set("pub", "p").build());
    }

    @Test
    public void emptiesPayloadOfEventOnRevokedPath() {
        final Adaptable adaptable = thingEvent(JsonPointer.of("/attributes/secret"), JsonValue.of("s"), null,
                headers(Map.of("attributes/secret", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE, GROUP_X));

        assertThat(filtered.getPayload().getValue()).contains(JsonObject.empty());
    }

    @Test
    public void emptiesPayloadOfEventBelowRevokedPath() {
        final Adaptable adaptable = thingEvent(JsonPointer.of("/attributes/secret/nested"), JsonValue.of(1), null,
                headers(Map.of("attributes/secret", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE, GROUP_X));

        assertThat(filtered.getPayload().getValue()).contains(JsonObject.empty());
    }

    @Test
    public void keepsPayloadOfSubscriberWithoutTheRevokedSubject() {
        final Adaptable adaptable = thingEvent(JsonPointer.empty(), THING, null,
                headers(Map.of("attributes/secret", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE));

        assertThat(payloadValue(filtered)).isEqualTo(THING);
    }

    @Test
    public void keepsSiblingWithCommonPrefix() {
        final JsonObject attributes = ATTRIBUTES.setValue("secretive", "v");
        final Adaptable adaptable = thingEvent(JsonPointer.of("/attributes"), attributes, null,
                headers(Map.of("attributes/secret", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE, GROUP_X));

        assertThat(payloadValue(filtered))
                .isEqualTo(JsonObject.newBuilder().set("pub", "p").set("secretive", "v").build());
    }

    @Test
    public void removesRevokedFieldFromExtraFields() {
        final JsonObject extra = JsonObject.newBuilder()
                .set("thingId", THING_ID.toString())
                .set("attributes", ATTRIBUTES)
                .build();
        final Adaptable adaptable = thingEvent(JsonPointer.of("/attributes/pub"), JsonValue.of("p"), extra,
                headers(Map.of("attributes/secret", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE, GROUP_X));

        assertThat(filtered.getPayload().getValue()).contains(JsonValue.of("p"));
        assertThat(filtered.getPayload().getExtra()).contains(JsonObject.newBuilder()
                .set("thingId", THING_ID.toString())
                .set("attributes", JsonObject.newBuilder().set("pub", "p").build())
                .build());
    }

    @Test
    public void appliesRevokesOnTopOfPartialAccessPaths() {
        final Adaptable adaptable = thingEvent(JsonPointer.empty(), THING, null,
                headers(Map.of("attributes/secret", GROUP_X), Map.of("attributes", PARTIAL)));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(PARTIAL, GROUP_X));

        assertThat(payloadValue(filtered).getValue(JsonPointer.of("/attributes")))
                .contains(JsonObject.newBuilder().set("pub", "p").build());
    }

    @Test
    public void revokedTopLevelFieldsKeepIdentityFields() {
        final Adaptable adaptable = thingEvent(JsonPointer.of("/attributes"), ATTRIBUTES, THING,
                headers(Map.of("thingId", GROUP_X, "policyId", GROUP_X, "attributes", GROUP_X), null));

        final Adaptable filtered =
                AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, context(ALICE, GROUP_X));

        assertThat(filtered.getPayload().getValue()).contains(JsonObject.empty());
        assertThat(filtered.getPayload().getExtra())
                .contains(JsonObject.newBuilder().set("thingId", THING_ID.toString()).build());
    }

    @Test
    public void leavesNonEventsUntouched() {
        final Adaptable command = ProtocolFactory.newAdaptableBuilder(
                        ProtocolFactory.newTopicPathBuilder(THING_ID).twin().commands().modify().build())
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(JsonPointer.empty())
                        .withValue(THING)
                        .build())
                .withHeaders(headers(Map.of("attributes/secret", GROUP_X), null))
                .build();

        assertThat(AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(command, context(ALICE, GROUP_X)))
                .isSameAs(command);
    }

    @Test
    public void removesRevokedFieldOnlyFromExtraFieldsOfMessages() {
        final Adaptable message = ProtocolFactory.newAdaptableBuilder(ProtocolFactory.newTopicPathBuilder(THING_ID)
                        .live().messages().subject("hello").build())
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(JsonPointer.of("/inbox/messages/hello"))
                        .withValue(JsonObject.newBuilder().set("secret", "message-content").build())
                        .withExtra(JsonObject.newBuilder()
                                .set("thingId", THING_ID.toString())
                                .set("attributes", ATTRIBUTES)
                                .build())
                        .build())
                .withHeaders(DittoHeaders.empty())
                .build();

        final Adaptable filtered = AdaptablePartialAccessFilter.removeRevokedPaths(message,
                Set.of(JsonPointer.of("/attributes/secret")));

        assertThat(filtered.getPayload().getValue()).isEqualTo(message.getPayload().getValue());
        assertThat(filtered.getPayload().getExtra()).contains(JsonObject.newBuilder()
                .set("thingId", THING_ID.toString())
                .set("attributes", JsonObject.newBuilder().set("pub", "p").build())
                .build());
    }

    @Test
    public void eventPathIsNotReadableBelowARevokedPath() {
        final DittoHeaders revoked = headers(Map.of("attributes/secret", GROUP_X), null);

        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.of("/attributes/secret"), JsonValue.of("s"), null, revoked),
                context(ALICE, GROUP_X))).isFalse();
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.of("/attributes"), ATTRIBUTES, null, revoked), context(ALICE, GROUP_X)))
                .isTrue();
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.of("/attributes/secret"), JsonValue.of("s"), null, revoked), context(ALICE)))
                .isTrue();
    }

    @Test
    public void eventPathIsReadableOnlyAtOrAroundAccessiblePaths() {
        final DittoHeaders partial = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        indexed(Map.of("attributes/pub", PARTIAL)).toString())
                .readGrantedSubjects(List.of(PARTIAL))
                .build();

        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.of("/attributes/secret"), JsonValue.of("s"), null, partial),
                context(PARTIAL))).isFalse();
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.of("/attributes"), ATTRIBUTES, null, partial), context(PARTIAL))).isTrue();
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.empty(), THING, null, partial), context(PARTIAL))).isTrue();
    }

    @Test
    public void eventPathIsReadableWithoutRestrictions() {
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.of("/attributes/secret"), JsonValue.of("s"), null, DittoHeaders.empty()),
                context(GROUP_X))).isTrue();
    }

    @Test
    public void thingRootEventIsNotReadableIfTheThingRootIsRevoked() {
        final DittoHeaders rootRevoked = headers(Map.of("thingId", GROUP_X, "policyId", GROUP_X,
                "attributes", GROUP_X), null);

        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.empty(), THING, null, rootRevoked), context(ALICE, GROUP_X))).isFalse();
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.empty(), THING, null, rootRevoked), context(ALICE))).isTrue();
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(
                thingEvent(JsonPointer.empty(), THING, null, headers(Map.of("attributes", GROUP_X), null)),
                context(ALICE, GROUP_X))).isTrue();
    }

    @Test
    public void eventIsNotReadableForPartialSubjectWithoutAccessiblePath() {
        final DittoHeaders partialWithoutPaths = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:partial\"],\"paths\":{}}")
                .readGrantedSubjects(List.of(PARTIAL, ALICE))
                .build();
        final Adaptable event = thingEvent(JsonPointer.of("/attributes"), ATTRIBUTES, null, partialWithoutPaths);

        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(event, context(PARTIAL))).isFalse();
        assertThat(AdaptablePartialAccessFilter.isEventPathReadable(event, context(PARTIAL, ALICE))).isTrue();
        assertThat(AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(event, context(PARTIAL, ALICE)))
                .isSameAs(event);
    }

    private static DittoHeaders headers(final Map<String, AuthorizationSubject> revokedPaths,
            @Nullable final Map<String, AuthorizationSubject> partialAccessPaths) {

        final DittoHeadersBuilder<?, ?> builder = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(), indexed(revokedPaths).toString());
        if (partialAccessPaths != null) {
            builder.putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                    indexed(partialAccessPaths).toString());
            builder.readGrantedSubjects(partialAccessPaths.values());
        }
        return builder.build();
    }

    private static JsonObject indexed(final Map<String, AuthorizationSubject> pathToSubject) {
        final List<String> subjectIds = pathToSubject.values().stream()
                .map(AuthorizationSubject::getId)
                .distinct()
                .toList();
        final JsonArrayBuilder subjects = JsonFactory.newArrayBuilder();
        subjectIds.forEach(subjects::add);
        final JsonObjectBuilder paths = JsonFactory.newObjectBuilder();
        pathToSubject.forEach((path, subject) -> paths.set(JsonFactory.newKey(path),
                JsonFactory.newArrayBuilder().add(subjectIds.indexOf(subject.getId())).build()));
        return JsonObject.newBuilder().set("subjects", subjects.build()).set("paths", paths.build()).build();
    }

    private static Adaptable thingEvent(final JsonPointer path, final JsonValue value,
            @Nullable final JsonObject extra, final DittoHeaders headers) {

        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(THING_ID).twin().events().modified().build();
        return ProtocolFactory.newAdaptableBuilder(topicPath)
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(path)
                        .withValue(value)
                        .withExtra(extra)
                        .build())
                .withHeaders(headers)
                .build();
    }

    private static JsonObject payloadValue(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().orElseThrow().asObject();
    }

    private static AuthorizationContext context(final AuthorizationSubject... subjects) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, subjects[0],
                Arrays.copyOfRange(subjects, 1, subjects.length));
    }

}
