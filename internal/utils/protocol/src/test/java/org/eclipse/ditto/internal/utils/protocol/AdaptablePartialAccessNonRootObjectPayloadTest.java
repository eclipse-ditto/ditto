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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Reproduces the ddm-device-status drop: a subject with partial read access loses every
 * non-root event whose value is a JSON object, even when the event path is fully granted.
 *
 * <p>The payload of a non-root event is relative to {@code payload.path}, but
 * {@code AdaptablePartialAccessFilter} feeds thing-absolute grant pointers straight into
 * {@code JsonPartialAccessFilter.filterJsonByPaths} for object payloads, so nothing matches
 * and the payload is emptied. Scalar payloads take a different branch that does consult the
 * event path, which is why the control case below passes.</p>
 */
public final class AdaptablePartialAccessNonRootObjectPayloadTest {

    private static final AuthorizationSubject SUBJECT_PARTIAL =
            AuthorizationSubject.newInstance("connection:ddm-device-status");

    /** Mirrors the real grant: READ on /features/deviceStatus (plus siblings, omitted here). */
    private static final String GRANT_ON_DEVICE_STATUS = JsonFactory.newObjectBuilder()
            .set("subjects", JsonFactory.newArrayBuilder()
                    .add(SUBJECT_PARTIAL.getId())
                    .build())
            .set("paths", JsonFactory.newObjectBuilder()
                    .set(JsonFactory.newKey("features/deviceStatus"),
                            JsonFactory.newArrayBuilder().add(0).build())
                    .build())
            .build()
            .toString();

    /** Same, plus /attributes — used where the extra fields must retain something. */
    private static final String GRANT_ON_DEVICE_STATUS_AND_ATTRIBUTES = JsonFactory.newObjectBuilder()
            .set("subjects", JsonFactory.newArrayBuilder()
                    .add(SUBJECT_PARTIAL.getId())
                    .build())
            .set("paths", JsonFactory.newObjectBuilder()
                    .set(JsonFactory.newKey("features/deviceStatus"),
                            JsonFactory.newArrayBuilder().add(0).build())
                    .set(JsonFactory.newKey("attributes"),
                            JsonFactory.newArrayBuilder().add(0).build())
                    .build())
            .build()
            .toString();

    /** The real merge value from the dev event: relative to path /features/deviceStatus. */
    private static JsonObject deviceStatusMergeValue() {
        return JsonFactory.newObjectBuilder()
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("technicalSeverity", JsonFactory.newObjectBuilder()
                                        .set("level", JsonFactory.newObjectBuilder()
                                                .set("name", "ERROR")
                                                .set("cardinality", 50)
                                                .build())
                                        .build())
                                .set("updatedAt", "2026-08-31T09:50:46.416636777Z")
                                .build())
                        .build())
                .build();
    }

    // ---------- control: scalar payload at a granted path (expected to pass today) ----------

    @Test
    public void controlScalarPayloadBelowGrantedPathIsDelivered() {
        final Adaptable adaptable = eventAdaptable(
                JsonPointer.of("features/deviceStatus/properties/status/updatedAt"),
                JsonValue.of("2026-08-31T09:50:46.416636777Z"));

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        assertThat(result.getPayload().getValue())
                .as("scalar payload under a granted path must survive")
                .contains(JsonValue.of("2026-08-31T09:50:46.416636777Z"));
    }

    // ---------- the bug: object payload at an exactly-granted path ----------

    @Test
    public void objectPayloadAtExactlyGrantedPathIsDelivered() {
        final JsonObject mergeValue = deviceStatusMergeValue();
        final Adaptable adaptable = eventAdaptable(JsonPointer.of("features/deviceStatus"), mergeValue);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        assertThat(payloadObject(result))
                .as("event path /features/deviceStatus is fully granted, so the whole merge value is readable")
                .isEqualTo(mergeValue);
    }

    @Test
    public void objectPayloadBelowGrantedPathIsDelivered() {
        final JsonObject value = JsonFactory.newObjectBuilder()
                .set("technicalSeverity", JsonFactory.newObjectBuilder().set("name", "ERROR").build())
                .build();
        final Adaptable adaptable =
                eventAdaptable(JsonPointer.of("features/deviceStatus/properties/status"), value);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        assertThat(payloadObject(result))
                .as("object payload strictly below a granted path must survive")
                .isEqualTo(value);
    }

    @Test
    public void objectPayloadAtParentOfGrantIsNarrowedNotEmptied() {
        // Event at /features carrying both a granted and a non-granted feature.
        final JsonObject value = JsonFactory.newObjectBuilder()
                .set("deviceStatus", JsonFactory.newObjectBuilder()
                        .set("properties", JsonFactory.newObjectBuilder().set("x", 1).build())
                        .build())
                .set("grid", JsonFactory.newObjectBuilder()
                        .set("properties", JsonFactory.newObjectBuilder().set("y", 2).build())
                        .build())
                .build();
        final Adaptable adaptable = eventAdaptable(JsonPointer.of("features"), value);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        final JsonObject filtered = payloadObject(result);
        assertThat(filtered.getValue(JsonPointer.of("/deviceStatus/properties/x")))
                .as("granted feature must survive an event rooted at /features")
                .isPresent();
        assertThat(filtered.getValue(JsonPointer.of("/grid")))
                .as("non-granted feature must be stripped")
                .isEmpty();
    }

    @Test
    public void objectPayloadAtNonGrantedPathIsStillDropped() {
        final JsonObject value = JsonFactory.newObjectBuilder()
                .set("properties", JsonFactory.newObjectBuilder().set("receiveTime", "2026-08-31T09:00:00Z").build())
                .build();
        final Adaptable adaptable = eventAdaptable(JsonPointer.of("features/grid"), value);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        assertThat(payloadObject(result))
                .as("an event outside every granted subtree must still be emptied")
                .isEmpty();
    }

    @Test
    public void extraFieldsAreStillFilteredWhenPayloadPassesThrough() {
        final JsonObject mergeValue = deviceStatusMergeValue();
        final JsonObject extra = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder().set("serial", "VA0380206336").build())
                .set("features", JsonFactory.newObjectBuilder()
                        .set("grid", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("receiveTime", "2026-08-31T09:00:00Z").build())
                                .build())
                        .build())
                .build();

        final Adaptable adaptable = eventAdaptableWithExtra(JsonPointer.of("features/deviceStatus"), mergeValue,
                extra, GRANT_ON_DEVICE_STATUS_AND_ATTRIBUTES);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        assertThat(payloadObject(result))
                .as("payload passes through untouched")
                .isEqualTo(mergeValue);

        final JsonObject filteredExtra = result.getPayload().getExtra().orElse(JsonFactory.newObject());
        assertThat(filteredExtra.getValue(JsonPointer.of("/attributes/serial")))
                .as("granted extra field must survive")
                .isPresent();
        assertThat(filteredExtra.getValue(JsonPointer.of("/features/grid")))
                .as("non-granted extra field must not leak when the payload passes through")
                .isEmpty();
    }

    @Test
    public void extraFieldsAreClearedWhenNothingInThemIsAccessible() {
        // Regression guard: the payload builder is seeded from the original payload, so an empty filter
        // result must still be written back — otherwise the full unfiltered extra is handed over.
        final JsonObject extra = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("grid", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("receiveTime", "2026-08-31T09:00:00Z").build())
                                .build())
                        .build())
                .build();

        final Adaptable adaptable = eventAdaptableWithExtra(JsonPointer.of("features/deviceStatus"),
                deviceStatusMergeValue(), extra, GRANT_ON_DEVICE_STATUS);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        assertThat(result.getPayload().getExtra().orElse(JsonFactory.newObject()))
                .as("wholly inaccessible extra must be cleared, not passed through unfiltered")
                .isEmpty();
    }

    @Test
    public void extraFieldsAreClearedWhenPayloadPathIsOutsideGrants() {
        // Regression guard: emptying the value is not enough on this branch either. The downstream drop guards
        // require a non-empty original value, so an event whose value was already an empty object is delivered
        // -- and would carry the original, unfiltered extra with it.
        final JsonObject extra = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("grid", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("receiveTime", "2026-08-31T09:00:00Z").build())
                                .build())
                        .build())
                .build();

        final Adaptable adaptable = eventAdaptableWithExtra(JsonPointer.of("features/grid"),
                JsonFactory.newObject(), extra, GRANT_ON_DEVICE_STATUS);

        final Adaptable result = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                adaptable, authContext());

        assertThat(payloadObject(result))
                .as("value outside every granted subtree must be emptied")
                .isEmpty();
        assertThat(result.getPayload().getExtra().orElse(JsonFactory.newObject()))
                .as("extra must be filtered here too, not passed through unfiltered")
                .isEmpty();
    }

    // ---------- helpers ----------

    private static JsonObject payloadObject(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonFactory.newObject());
    }

    private static AuthorizationContext authContext() {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, SUBJECT_PARTIAL);
    }

    private static Adaptable eventAdaptable(final JsonPointer path, final JsonValue value) {
        return build(path, value, null, GRANT_ON_DEVICE_STATUS);
    }

    private static Adaptable eventAdaptableWithExtra(final JsonPointer path, final JsonValue value,
            final JsonObject extra, final String grantHeader) {
        return build(path, value, extra, grantHeader);
    }

    private static Adaptable build(final JsonPointer path, final JsonValue value,
            @Nullable final JsonObject extra, final String grantHeader) {

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), grantHeader)
                .readGrantedSubjects(Set.of(SUBJECT_PARTIAL))
                .build();
        final ThingId thingId = ThingId.of("io.beyonnex.smartheating.srt", "eui001bc507317d9419");
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(thingId)
                .things()
                .twin()
                .events()
                .merged()
                .build();
        final var payloadBuilder = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .withValue(value);
        if (extra != null) {
            payloadBuilder.withExtra(extra);
        }
        return ProtocolFactory.newAdaptableBuilder(topicPath)
                .withPayload(payloadBuilder.build())
                .withHeaders(headers)
                .build();
    }
}
