/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.api;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.api.persistence.SnapshotTaken;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ThingSnapshotTaken}.
 */
public final class ThingSnapshotTakenTest {

    private static final PolicyId POLICY_ID = TestConstants.Thing.POLICY_ID;
    private static final long REVISION = 23L;

    private static Instant timestamp;
    private static JsonObject thingJson;

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void beforeClass() {
        final var localDateTime = LocalDateTime.of(2021, Month.MARCH, 9, 10, 31, 50);
        timestamp = localDateTime.toInstant(ZoneOffset.UTC);

        thingJson = TestConstants.Thing.THING.toJson();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingSnapshotTaken.class, areImmutable(), provided(PolicyId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingSnapshotTaken.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void gettersReturnExpected() {
        final var lifecycle = PersistenceLifecycle.ACTIVE;
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var underTest = ThingSnapshotTaken.newBuilder(TestConstants.Thing.THING_ID, REVISION, lifecycle, thingJson)
                .policyId(POLICY_ID)
                .timestamp(timestamp)
                .dittoHeaders(dittoHeaders)
                .build();

        softly.assertThat((CharSequence) underTest.getEntityId()).as("entity ID").isEqualTo(TestConstants.Thing.THING_ID);
        softly.assertThat(underTest.getPolicyId()).as("policy ID").hasValue(POLICY_ID);
        softly.assertThat(underTest.getLifecycle()).as("lifecycle").isEqualTo(lifecycle);
        softly.assertThat(underTest.getPubSubTopic())
                .as("pub-sub topic")
                .isEqualTo(ThingSnapshotTaken.PUB_SUB_TOPIC);
        softly.assertThat(underTest.getEntity()).as("entity").hasValue(thingJson);
        softly.assertThat(underTest.getRevision()).as("revision").isEqualTo(REVISION);
        softly.assertThat(underTest.getType()).as("type").isEqualTo(ThingSnapshotTaken.TYPE);
        softly.assertThat(underTest.getManifest()).as("manifest").isEqualTo(underTest.getType());
        softly.assertThat(underTest.getTimestamp()).as("timestamp").hasValue(timestamp);
        softly.assertThat(underTest.getMetadata()).as("metadata").isEmpty();
        softly.assertThat(underTest.getDittoHeaders()).as("Ditto headers").isEqualTo(dittoHeaders);
        softly.assertThat((CharSequence) underTest.getResourcePath())
                .as("resource path")
                .isEqualTo(JsonPointer.empty());
        softly.assertThat(underTest.getResourceType())
                .as("resource type")
                .isEqualTo(ThingSnapshotTaken.RESOURCE_TYPE);
    }

    @Test
    public void toJsonReturnsExpected() {
        final var lifecycle = PersistenceLifecycle.DELETED;
        final var underTest = ThingSnapshotTaken.newBuilder(TestConstants.Thing.THING_ID, REVISION, lifecycle, thingJson)
                .policyId(POLICY_ID)
                .timestamp(timestamp)
                .build();

        final var jsonObject = JsonObject.newBuilder()
                .set(Event.JsonFields.TYPE, underTest.getType())
                .set(EventsourcedEvent.JsonFields.REVISION, underTest.getRevision())
                .set(Event.JsonFields.TIMESTAMP, timestamp.toString())
                .set(SnapshotTaken.JsonFields.ENTITY_ID, String.valueOf(underTest.getEntityId()))
                .set(SnapshotTaken.JsonFields.ENTITY, thingJson)
                .set(SnapshotTaken.JsonFields.LIFECYCLE, lifecycle.name())
                .set(ThingSnapshotTaken.JSON_POLICY_ID, POLICY_ID.toString())
                .build();

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromJsonWorksAsExpected() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();

        final var thingSnapshotTaken =
                ThingSnapshotTaken.newBuilder(TestConstants.Thing.THING_ID, REVISION, PersistenceLifecycle.ACTIVE, thingJson)
                        .timestamp(timestamp)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final var underTest = ThingSnapshotTaken.fromJson(thingSnapshotTaken.toJson(), dittoHeaders);

        assertThat(underTest).isEqualTo(thingSnapshotTaken);
    }

    @Test
    public void setDittoHeadersReturnsInstanceWithNewDittoHeaders() {
        final var lifecycle = PersistenceLifecycle.ACTIVE;
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var underTest = ThingSnapshotTaken.newBuilder(TestConstants.Thing.THING_ID, REVISION, lifecycle, thingJson)
                .policyId(POLICY_ID)
                .timestamp(timestamp)
                .build();

        final var thingSnapshotTakenWithNewDittoHeaders = underTest.setDittoHeaders(dittoHeaders);

        softly.assertThat((CharSequence) underTest.getEntityId()).as("entity ID").isEqualTo(TestConstants.Thing.THING_ID);
        softly.assertThat(underTest.getPolicyId()).as("policy ID").isEqualTo(underTest.getPolicyId());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getDittoHeaders())
                .as("Ditto headers")
                .isEqualTo(dittoHeaders);
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getRevision())
                .as("revision")
                .isEqualTo(underTest.getRevision());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getLifecycle())
                .as("lifecycle")
                .isEqualTo(underTest.getLifecycle());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getPubSubTopic())
                .as("pub-sub topic")
                .isEqualTo(underTest.getPubSubTopic());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getEntity())
                .as("entity")
                .isEqualTo(underTest.getEntity());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getType())
                .as("type")
                .isEqualTo(underTest.getType());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getManifest())
                .as("manifest")
                .isEqualTo(underTest.getManifest());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getTimestamp())
                .as("timestamp")
                .isEqualTo(underTest.getTimestamp());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getMetadata())
                .as("metadata")
                .isEqualTo(underTest.getMetadata());
        softly.assertThat((CharSequence) thingSnapshotTakenWithNewDittoHeaders.getResourcePath())
                .as("resource path")
                .isEqualTo(underTest.getResourcePath());
        softly.assertThat(thingSnapshotTakenWithNewDittoHeaders.getResourceType())
                .as("resource type")
                .isEqualTo(underTest.getResourceType());
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
    }

}
