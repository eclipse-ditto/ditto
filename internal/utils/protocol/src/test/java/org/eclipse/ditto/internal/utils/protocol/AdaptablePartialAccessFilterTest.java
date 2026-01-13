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

import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import java.util.Optional;

import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
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

    @Test
    public void strictMatchingDeniesRevokedPathsForNonObjectPayloads() {
        // GIVEN: A user has access to /attributes/complex but NOT to /attributes/complex/secret (revoked)
        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(SUBJECT_PARTIAL.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/complex"), JsonFactory.newArrayBuilder().add(0).build())
                        .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        // WHEN: An event for /attributes/complex/secret is received (revoked path)
        final Adaptable adaptable = createThingEventAdaptable(
                JsonPointer.of("attributes/complex/secret"),
                JsonValue.of("secret-value"),
                headers);

        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        // THEN: The event should be filtered out (empty payload)
        final JsonObject filteredPayload = result.getPayload().getValue()
                .filter(org.eclipse.ditto.json.JsonValue::isObject)
                .map(org.eclipse.ditto.json.JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload).isEmpty();
    }

    @Test
    public void strictMatchingAllowsExactMatchesForNonObjectPayloads() {
        // GIVEN: A user has access to /attributes/complex/some
        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(SUBJECT_PARTIAL.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        // WHEN: An event for /attributes/complex/some is received (exact match)
        final Adaptable adaptable = createThingEventAdaptable(
                JsonPointer.of("attributes/complex/some"),
                JsonValue.of(42),
                headers);

        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        // THEN: The event should be allowed
        assertThat(result.getPayload().getValue()).isPresent();
        assertThat(result.getPayload().getValue().orElse(JsonFactory.nullLiteral())).isEqualTo(JsonValue.of(42));
    }

    @Test
    public void strictMatchingDeniesParentPathWhenChildIsRevoked() {
        // GIVEN: A user has access to /attributes/complex/some but /attributes/complex/secret is revoked
        // This means /attributes/complex should NOT be in accessible paths (parent with revoked child)
        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(SUBJECT_PARTIAL.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        // WHEN: An event for /attributes/complex/secret is received (revoked path)
        final Adaptable adaptable = createThingEventAdaptable(
                JsonPointer.of("attributes/complex/secret"),
                JsonValue.of("secret-value"),
                headers);

        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        // THEN: The event should be filtered out (empty payload)
        final JsonObject filteredPayload = result.getPayload().getValue()
                .filter(org.eclipse.ditto.json.JsonValue::isObject)
                .map(org.eclipse.ditto.json.JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload).isEmpty();
    }

    @Test
    public void strictMatchingAllowsMultipleAccessiblePaths() {
        // GIVEN: A user has access to multiple specific paths
        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(SUBJECT_PARTIAL.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/type"), JsonFactory.newArrayBuilder().add(0).build())
                        .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).build())
                        .set(JsonFactory.newKey("features/some/properties/configuration/foo"),
                                JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        // WHEN: Events for accessible paths are received
        final Adaptable adaptable1 = createThingEventAdaptable(
                JsonPointer.of("attributes/type"),
                JsonValue.of("LORAWAN_GATEWAY"),
                headers);
        final Adaptable adaptable2 = createThingEventAdaptable(
                JsonPointer.of("attributes/complex/some"),
                JsonValue.of(42),
                headers);
        final Adaptable adaptable3 = createThingEventAdaptable(
                JsonPointer.of("features/some/properties/configuration/foo"),
                JsonValue.of(456),
                headers);

        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        // THEN: All should be allowed
        final Adaptable result1 = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable1, context);
        final Adaptable result2 = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable2, context);
        final Adaptable result3 = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable3, context);

        assertThat(result1.getPayload().getValue()).isPresent();
        assertThat(result2.getPayload().getValue()).isPresent();
        assertThat(result3.getPayload().getValue()).isPresent();
    }

    @Test
    public void strictMatchingDeniesInaccessiblePaths() {
        // GIVEN: A user has access to /attributes/type but NOT to /attributes/hidden
        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(SUBJECT_PARTIAL.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/type"), JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        // WHEN: An event for /attributes/hidden is received (not accessible)
        final Adaptable adaptable = createThingEventAdaptable(
                JsonPointer.of("attributes/hidden"),
                JsonValue.of(false),
                headers);

        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        // THEN: The event should be filtered out (empty payload)
        final JsonObject filteredPayload = result.getPayload().getValue()
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

    private static Adaptable createThingEventAdaptable(final JsonPointer path, final JsonValue value, final DittoHeaders headers) {
        final ThingId thingId = ThingId.of("org.eclipse.ditto.test", "thing");
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(thingId)
                .events()
                .modified()
                .build();
        return ProtocolFactory.newAdaptableBuilder(topicPath)
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(path)
                        .withValue(value)
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

    @Test
    public void filtersExtraFieldsForPartialAccess() {
        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(SUBJECT_PARTIAL.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/type"), JsonFactory.newArrayBuilder().add(0).build())
                        .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        final JsonObject payload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("type", JsonValue.of("LORAWAN_GATEWAY"))
                        .set("hidden", JsonValue.of(false))
                        .set("complex", JsonFactory.newObjectBuilder()
                                .set("some", JsonValue.of(42))
                                .set("secret", JsonValue.of("pssst"))
                                .build())
                        .build())
                .build();

        final JsonObject extraFields = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("type", JsonValue.of("LORAWAN_GATEWAY"))
                        .set("hidden", JsonValue.of(false))
                        .set("complex", JsonFactory.newObjectBuilder()
                                .set("some", JsonValue.of(42))
                                .set("secret", JsonValue.of("pssst"))
                                .build())
                        .build())
                .set("features", JsonFactory.newObjectBuilder()
                        .set("temp", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("value", JsonValue.of(25.5))
                                        .build())
                                .build())
                        .build())
                .build();

        final Adaptable adaptable = createThingEventAdaptableWithExtra(payload, extraFields, headers);
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        final JsonObject filteredPayload = result.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/type"))).isPresent();
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/hidden"))).isEmpty();
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/complex/some"))).isPresent();
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/complex/secret"))).isEmpty();

        final Optional<JsonObject> filteredExtra = result.getPayload().getExtra();
        assertThat(filteredExtra).isPresent();
        final JsonObject extraObj = filteredExtra.get();
        assertThat(extraObj.getValue(JsonPointer.of("/attributes/type"))).isPresent();
        assertThat(extraObj.getValue(JsonPointer.of("/attributes/hidden"))).isEmpty();
        if (extraObj.getValue(JsonPointer.of("/attributes/complex")).isPresent()) {
            final JsonValue complexValue = extraObj.getValue(JsonPointer.of("/attributes/complex")).get();
            if (complexValue.isObject()) {
                assertThat(complexValue.asObject().getValue(JsonPointer.of("some"))).isPresent();
                assertThat(complexValue.asObject().getValue(JsonPointer.of("secret"))).isEmpty();
            }
        }
        assertThat(extraObj.getValue(JsonPointer.of("/features"))).isEmpty();

        final Adaptable nonObjectAdaptable = createThingEventAdaptableWithExtra(
                JsonPointer.of("attributes/type"),
                JsonValue.of("LORAWAN_GATEWAY_V2"),
                extraFields,
                headers);

        final Adaptable nonObjectResult = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                nonObjectAdaptable, context);

        assertThat(nonObjectResult.getPayload().getValue()).isPresent();
        assertThat(nonObjectResult.getPayload().getValue().get()).isEqualTo(JsonValue.of("LORAWAN_GATEWAY_V2"));

        final Optional<JsonObject> nonObjectFilteredExtra = nonObjectResult.getPayload().getExtra();
        assertThat(nonObjectFilteredExtra).isPresent();
        final JsonObject nonObjectExtraObj = nonObjectFilteredExtra.get();

        assertThat(nonObjectExtraObj.getValue(JsonPointer.of("/attributes/type"))).isPresent();
        assertThat(nonObjectExtraObj.getValue(JsonPointer.of("/attributes/complex/some"))).isPresent();
        assertThat(nonObjectExtraObj.getValue(JsonPointer.of("/attributes/hidden"))).isEmpty();
        assertThat(nonObjectExtraObj.getValue(JsonPointer.of("/features"))).isEmpty();
    }


    @Test
    public void preservesExtraFieldsWhenAllPathsAreAccessible() {
        // GIVEN: A user has full access (unrestricted)
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), PARTIAL_ACCESS_HEADER)
                .readGrantedSubjects(Set.of(SUBJECT_FULL))
                .build();

        final JsonObject payload = createThingPayload();
        final JsonObject extraFields = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("public", JsonValue.of("public-value"))
                        .set("private", JsonValue.of("private-value"))
                        .build())
                .set("features", JsonFactory.newObjectBuilder()
                        .set("temp", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("value", JsonValue.of(25.5))
                                        .build())
                                .build())
                        .build())
                .build();

        final Adaptable adaptable = createThingEventAdaptableWithExtra(payload, extraFields, headers);
        final AuthorizationContext context = authContext(SUBJECT_FULL);

        // WHEN: Filtering is applied (should return original due to unrestricted access)
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        // THEN: Original adaptable should be returned (no filtering for unrestricted access)
        assertThat(result).isSameAs(adaptable);
        // ExtraFields should be preserved
        assertThat(result.getPayload().getExtra()).isPresent();
        assertThat(result.getPayload().getExtra().get()).isEqualTo(extraFields);
    }

    @Test
    public void handlesExtraFieldsWithNestedStructures() {
        // GIVEN: A user has access to specific nested paths
        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(SUBJECT_PARTIAL.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/type"), JsonFactory.newArrayBuilder().add(0).build())
                        .set(JsonFactory.newKey("features/temp/properties/value"),
                                JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        final JsonObject payload = createThingPayload();
        // ExtraFields with nested structures
        final JsonObject extraFields = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("type", JsonValue.of("LORAWAN_GATEWAY"))
                        .set("hidden", JsonValue.of(false))
                        .build())
                .set("features", JsonFactory.newObjectBuilder()
                        .set("temp", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("value", JsonValue.of(25.5))
                                        .set("unit", JsonValue.of("celsius"))
                                        .build())
                                .build())
                        .set("other", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("secret", JsonValue.of("hidden"))
                                        .build())
                                .build())
                        .build())
                .build();

        final Adaptable adaptable = createThingEventAdaptableWithExtra(payload, extraFields, headers);
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        // WHEN: Filtering is applied
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        // THEN: ExtraFields should be filtered correctly
        final Optional<JsonObject> filteredExtra = result.getPayload().getExtra();
        assertThat(filteredExtra).isPresent();
        final JsonObject extraObj = filteredExtra.get();

        // Accessible paths should be present
        assertThat(extraObj.getValue(JsonPointer.of("/attributes/type"))).isPresent();
        assertThat(extraObj.getValue(JsonPointer.of("/features/temp/properties/value"))).isPresent();

        // Inaccessible paths should be filtered
        assertThat(extraObj.getValue(JsonPointer.of("/attributes/hidden"))).isEmpty();
        assertThat(extraObj.getValue(JsonPointer.of("/features/temp/properties/unit"))).isEmpty();
        assertThat(extraObj.getValue(JsonPointer.of("/features/other"))).isEmpty();
    }

    @Test
    public void handlesAdaptableWithoutExtraFields() {
        // GIVEN: An adaptable without extraFields
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), PARTIAL_ACCESS_HEADER)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();

        final JsonObject payload = createThingPayload();
        final Adaptable adaptable = createThingEventAdaptable(payload, headers);
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        // WHEN: Filtering is applied
        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, context);

        // THEN: Should work normally (no extraFields to filter)
        assertThat(result.getPayload().getExtra()).isEmpty();
        final JsonObject filteredPayload = result.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonFactory.newObject());
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/public"))).isPresent();
        assertThat(filteredPayload.getValue(JsonPointer.of("/attributes/private"))).isEmpty();
    }

    @Test
    public void filtersExtraFieldsUsingFilterAdaptableWithResult() {
        // GIVEN: Pre-resolved accessible paths
        final JsonObject payload = createThingPayload();
        final JsonObject extraFields = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("public", JsonValue.of("public-value"))
                        .set("private", JsonValue.of("private-value"))
                        .build())
                .build();

        final Adaptable adaptable = createThingEventAdaptableWithExtra(payload, extraFields, DittoHeaders.empty());
        final PartialAccessPathResolver.AccessiblePathsResult result =
                PartialAccessPathResolver.AccessiblePathsResult.filtered(
                        Set.of(JsonPointer.of("/attributes/public"),
                                JsonPointer.of("/features/temp/properties/value")));

        // WHEN: Filtering is applied using pre-resolved result
        final Adaptable filtered = AdaptablePartialAccessFilter.filterAdaptableWithResult(adaptable, result);

        // THEN: ExtraFields should be filtered
        final Optional<JsonObject> filteredExtra = filtered.getPayload().getExtra();
        assertThat(filteredExtra).isPresent();
        final JsonObject extraObj = filteredExtra.get();
        assertThat(extraObj.getValue(JsonPointer.of("/attributes/public"))).isPresent();
        assertThat(extraObj.getValue(JsonPointer.of("/attributes/private"))).isEmpty();
    }

    private static Adaptable createThingEventAdaptableWithExtra(
            final JsonObject payload,
            final JsonObject extraFields,
            final DittoHeaders headers) {
        final ThingId thingId = ThingId.of("org.eclipse.ditto.test", "thing");
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(thingId)
                .events()
                .modified()
                .build();
        return ProtocolFactory.newAdaptableBuilder(topicPath)
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(JsonPointer.empty())
                        .withValue(payload)
                        .withExtra(extraFields)
                        .build())
                .withHeaders(headers)
                .build();
    }


    private static Adaptable createThingEventAdaptableWithExtra(
            final JsonPointer path,
            final JsonValue value,
            final JsonObject extraFields,
            final DittoHeaders headers) {
        final ThingId thingId = ThingId.of("org.eclipse.ditto.test", "thing");
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(thingId)
                .events()
                .modified()
                .build();
        return ProtocolFactory.newAdaptableBuilder(topicPath)
                .withPayload(ProtocolFactory.newPayloadBuilder()
                        .withPath(path)
                        .withValue(value)
                        .withExtra(extraFields)
                        .build())
                .withHeaders(headers)
                .build();
    }

    private static AuthorizationContext authContext(final AuthorizationSubject subject) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, subject);
    }
}
