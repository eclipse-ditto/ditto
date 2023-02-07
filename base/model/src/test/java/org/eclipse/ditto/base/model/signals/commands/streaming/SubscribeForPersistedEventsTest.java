/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SubscribeForPersistedEvents}.
 */
public final class SubscribeForPersistedEventsTest {

    private static final String KNOWN_ENTITY_ID_STR = "foo:bar";
    private static final String KNOWN_ENTITY_TYPE_STR = "thing";
    private static final String KNOWN_RESOURCE_PATH = "/";

    private static final long KNOWN_FROM_REV = 23L;
    private static final long KNOWN_TO_REV = 42L;
    private static final String KNOWN_FROM_TS = "2022-10-25T14:00:00Z";
    private static final String KNOWN_TO_TS = "2022-10-25T15:00:00Z";

    private static final String JSON_ALL_FIELDS = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SubscribeForPersistedEvents.TYPE)
            .set(StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_TYPE, KNOWN_ENTITY_TYPE_STR)
            .set(StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_ID, KNOWN_ENTITY_ID_STR)
            .set(StreamingSubscriptionCommand.JsonFields.JSON_RESOURCE_PATH, KNOWN_RESOURCE_PATH)
            .set(SubscribeForPersistedEvents.JsonFields.JSON_FROM_HISTORICAL_REVISION, KNOWN_FROM_REV)
            .set(SubscribeForPersistedEvents.JsonFields.JSON_TO_HISTORICAL_REVISION, KNOWN_TO_REV)
            .set(SubscribeForPersistedEvents.JsonFields.JSON_FROM_HISTORICAL_TIMESTAMP, KNOWN_FROM_TS)
            .set(SubscribeForPersistedEvents.JsonFields.JSON_TO_HISTORICAL_TIMESTAMP, KNOWN_TO_TS)
            .build()
            .toString();

    private static final String JSON_MINIMAL = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SubscribeForPersistedEvents.TYPE)
            .set(StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_TYPE, KNOWN_ENTITY_TYPE_STR)
            .set(StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_ID, KNOWN_ENTITY_ID_STR)
            .set(StreamingSubscriptionCommand.JsonFields.JSON_RESOURCE_PATH, KNOWN_RESOURCE_PATH)
            .set(SubscribeForPersistedEvents.JsonFields.JSON_FROM_HISTORICAL_REVISION, KNOWN_FROM_REV)
            .set(SubscribeForPersistedEvents.JsonFields.JSON_TO_HISTORICAL_REVISION, KNOWN_TO_REV)
            .build().toString();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubscribeForPersistedEvents.class,
                areImmutable(),
                provided(Instant.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubscribeForPersistedEvents.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonWithAllFieldsSet() {
        final SubscribeForPersistedEvents command = SubscribeForPersistedEvents.of(
                NamespacedEntityId.of(EntityType.of(KNOWN_ENTITY_TYPE_STR), KNOWN_ENTITY_ID_STR),
                JsonPointer.of(KNOWN_RESOURCE_PATH),
                KNOWN_FROM_REV,
                KNOWN_TO_REV,
                Instant.parse(KNOWN_FROM_TS),
                Instant.parse(KNOWN_TO_TS),
                DittoHeaders.empty()
        );

        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_ALL_FIELDS);
    }

    @Test
    public void toJsonWithOnlyRequiredFieldsSet() {
        final SubscribeForPersistedEvents command = SubscribeForPersistedEvents.of(
                NamespacedEntityId.of(EntityType.of(KNOWN_ENTITY_TYPE_STR), KNOWN_ENTITY_ID_STR),
                JsonPointer.of(KNOWN_RESOURCE_PATH),
                KNOWN_FROM_REV,
                KNOWN_TO_REV,
                DittoHeaders.empty());
        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_MINIMAL);
    }

    @Test
    public void fromJsonWithAllFieldsSet() {
        final SubscribeForPersistedEvents command = SubscribeForPersistedEvents.of(
                NamespacedEntityId.of(EntityType.of(KNOWN_ENTITY_TYPE_STR), KNOWN_ENTITY_ID_STR),
                JsonPointer.of(KNOWN_RESOURCE_PATH),
                KNOWN_FROM_REV,
                KNOWN_TO_REV,
                Instant.parse(KNOWN_FROM_TS),
                Instant.parse(KNOWN_TO_TS),
                DittoHeaders.empty()
        );
        assertThat(SubscribeForPersistedEvents.fromJson(JsonObject.of(JSON_ALL_FIELDS), DittoHeaders.empty()))
                .isEqualTo(command);
    }

    @Test
    public void fromJsonWithOnlyRequiredFieldsSet() {
        assertThat(SubscribeForPersistedEvents.fromJson(JsonObject.of(JSON_MINIMAL), DittoHeaders.empty()))
                .isEqualTo(SubscribeForPersistedEvents.of(
                        NamespacedEntityId.of(EntityType.of(KNOWN_ENTITY_TYPE_STR), KNOWN_ENTITY_ID_STR),
                        JsonPointer.of(KNOWN_RESOURCE_PATH),
                        KNOWN_FROM_REV,
                        KNOWN_TO_REV,
                        DittoHeaders.empty()));
    }

}
