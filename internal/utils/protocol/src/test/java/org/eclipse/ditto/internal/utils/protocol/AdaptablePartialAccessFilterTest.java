/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Unit tests for {@link AdaptablePartialAccessFilter}.
 */
public final class AdaptablePartialAccessFilterTest {

    private static final AuthorizationSubject SUBJECT_PARTIAL =
            AuthorizationSubject.newInstance("test:partial");
    private static final AuthorizationSubject SUBJECT_FULL =
            AuthorizationSubject.newInstance("test:full");

    private static final String PARTIAL_ACCESS_HEADER = JsonFactory.newObjectBuilder()
            .set("subjects", JsonFactory.newArrayBuilder()
                    .add(SUBJECT_PARTIAL.getId())
                    .build())
            .set("paths", JsonFactory.newObjectBuilder()
                    .set(JsonFactory.newKey("attributes/public"), JsonFactory.newArrayBuilder().add(0).build())
                    .set(JsonFactory.newKey("features/temp/properties/value"),
                            JsonFactory.newArrayBuilder().add(0).build())
                    .build())
            .build()
            .toString();

    @Test
    public void returnsOriginalWhenHeaderMissing() {
        final Adaptable adaptable = createThingEventAdaptable(createThingPayload(), DittoHeaders.empty());
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        assertThat(result).isSameAs(adaptable);
    }

    @Test
    public void returnsOriginalWhenNotThingEvent() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), PARTIAL_ACCESS_HEADER)
                .build();
        final Adaptable adaptable = createCommandAdaptable(headers);
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        assertThat(result).isSameAs(adaptable);
    }

    @Test
    public void returnsOriginalWhenUnrestrictedAccess() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), PARTIAL_ACCESS_HEADER)
                .readGrantedSubjects(Set.of(SUBJECT_FULL))
                .build();
        final Adaptable adaptable = createThingEventAdaptable(createThingPayload(), headers);
        final AuthorizationContext context = authContext(SUBJECT_FULL);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        assertThat(result).isSameAs(adaptable);
    }

    @Test
    public void filtersPayloadForPartialAccess() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), PARTIAL_ACCESS_HEADER)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();
        final JsonObject fullPayload = createThingPayload();
        final Adaptable adaptable = createThingEventAdaptable(fullPayload, headers);
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        assertThat(result).isNotSameAs(adaptable);
        final JsonObject filteredPayload = result.getPayload().getValue()
                .filter(org.eclipse.ditto.json.JsonValue::isObject)
                .map(org.eclipse.ditto.json.JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/public"))).isPresent();
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/private"))).isEmpty();
        assertThat(filteredPayload.getValue(JsonPointer.of("/features/temp/properties/value"))).isPresent();
    }

    @Test
    public void returnsEmptyPayloadWhenNoAccess() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), PARTIAL_ACCESS_HEADER)
                .readGrantedSubjects(Set.of())
                .build();
        final Adaptable adaptable = createThingEventAdaptable(createThingPayload(), headers);
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        final JsonObject filteredPayload = result.getPayload().getValue()
                .filter(org.eclipse.ditto.json.JsonValue::isObject)
                .map(org.eclipse.ditto.json.JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload).isEmpty();
    }

    @Test
    public void filterAdaptableWithResultFiltersBasedOnPreResolvedResult() {
        final JsonObject fullPayload = createThingPayload();
        final Adaptable adaptable = createThingEventAdaptable(fullPayload, DittoHeaders.empty());
        final PartialAccessPathResolver.AccessiblePathsResult result =
                PartialAccessPathResolver.AccessiblePathsResult.filtered(
                        Set.of(JsonPointer.of("/attributes/public"),
                                JsonPointer.of("/features/temp/properties/value")));

        final Adaptable filtered = AdaptablePartialAccessFilter.filterAdaptableWithResult(adaptable, result);

        final JsonObject filteredPayload = filtered.getPayload().getValue()
                .filter(org.eclipse.ditto.json.JsonValue::isObject)
                .map(org.eclipse.ditto.json.JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/public"))).isPresent();
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/private"))).isEmpty();
    }

    @Test
    public void filterAdaptableWithResultReturnsOriginalWhenUnrestricted() {
        final Adaptable adaptable = createThingEventAdaptable(createThingPayload(), DittoHeaders.empty());
        final PartialAccessPathResolver.AccessiblePathsResult result =
                PartialAccessPathResolver.AccessiblePathsResult.unrestricted();

        final Adaptable filtered = AdaptablePartialAccessFilter.filterAdaptableWithResult(adaptable, result);

        assertThat(filtered).isSameAs(adaptable);
    }

    @Test
    public void filterAdaptableWithResultReturnsEmptyWhenNoAccess() {
        final Adaptable adaptable = createThingEventAdaptable(createThingPayload(), DittoHeaders.empty());
        final PartialAccessPathResolver.AccessiblePathsResult result =
                PartialAccessPathResolver.AccessiblePathsResult.noAccess();

        final Adaptable filtered = AdaptablePartialAccessFilter.filterAdaptableWithResult(adaptable, result);

        final JsonObject filteredPayload = filtered.getPayload().getValue()
                .filter(org.eclipse.ditto.json.JsonValue::isObject)
                .map(org.eclipse.ditto.json.JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload).isEmpty();
    }

    private static JsonObject createThingPayload() {
        return JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("public", JsonFactory.newValue("public-value"))
                        .set("private", JsonFactory.newValue("private-value"))
                        .build())
                .set("features", JsonFactory.newObjectBuilder()
                        .set("temp", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("value", JsonFactory.newValue(25.5))
                                        .set("unit", JsonFactory.newValue("celsius"))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static Adaptable createThingEventAdaptable(final JsonObject payload, final DittoHeaders headers) {
        final ThingId thingId = ThingId.of("org.eclipse.ditto.test", "thing");
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(thingId)
                .events()
                .modified()
                .build();
        return ProtocolFactory.newAdaptableBuilder(topicPath)
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(JsonPointer.empty())
                        .withValue(payload)
                        .build())
                .withHeaders(headers)
                .build();
    }

    private static Adaptable createCommandAdaptable(final DittoHeaders headers) {
        final ThingId thingId = ThingId.of("org.eclipse.ditto.test", "thing");
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(thingId)
                .twin()
                .commands()
                .modify()
                .build();
        return ProtocolFactory.newAdaptableBuilder(topicPath)
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(JsonPointer.empty())
                        .withValue(JsonFactory.newObject())
                        .build())
                .withHeaders(headers)
                .build();
    }

    private static AuthorizationContext authContext(final AuthorizationSubject subject) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, subject);
    }
}
