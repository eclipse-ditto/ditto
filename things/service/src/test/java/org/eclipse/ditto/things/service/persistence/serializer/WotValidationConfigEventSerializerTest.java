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
package org.eclipse.ditto.things.service.persistence.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigCreated;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigDeleted;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.AfterClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link WotValidationConfigEventSerializer}: the derived journal tags and the event type guards.
 */
public final class WotValidationConfigEventSerializerTest {

    private static final ExtendedActorSystem SYSTEM = (ExtendedActorSystem) ActorSystem.create(
            "WotValidationConfigEventSerializerTest", ConfigFactory.load("test"));

    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");
    private static final WotValidationConfigId CONFIG_ID = WotValidationConfigId.of("serializer:wot");

    private final WotValidationConfigEventSerializer underTest = new WotValidationConfigEventSerializer(SYSTEM);

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(SYSTEM);
    }

    @Test
    public void getJournalTagsDerivesTheTagFromALowerCaseEventTypeSuffix() {
        assertThat(underTest.getJournalTags(wotEventOfType("wot:created")))
                .containsExactly("wot-validation-config-created");
        assertThat(underTest.getJournalTags(wotEventOfType("wot:modified")))
                .containsExactly("wot-validation-config-modified");
        assertThat(underTest.getJournalTags(wotEventOfType("wot:deleted")))
                .containsExactly("wot-validation-config-deleted");
        assertThat(underTest.getJournalTags(wotEventOfType("wot:other")))
                .containsExactly("wot-validation-config");
    }

    /**
     * The real event types are camel case ({@code …:wotValidationConfigCreated}); the case-sensitive match of today's
     * adapter therefore tags them with the bare prefix. This pins that behaviour so the journal tags do not change.
     */
    @Test
    public void getJournalTagsOfTheRealEventTypesIsTheBarePrefix() {
        final WotValidationConfig config = config();
        assertThat(underTest.getJournalTags(
                WotValidationConfigCreated.of(CONFIG_ID, config, 1L, TIMESTAMP, DittoHeaders.empty(), null)))
                .containsExactly("wot-validation-config");
        assertThat(underTest.getJournalTags(
                WotValidationConfigModified.of(CONFIG_ID, config, 2L, TIMESTAMP, DittoHeaders.empty(), null)))
                .containsExactly("wot-validation-config");
        assertThat(underTest.getJournalTags(
                WotValidationConfigDeleted.of(CONFIG_ID, 3L, TIMESTAMP, DittoHeaders.empty(), null)))
                .containsExactly("wot-validation-config");
    }

    @Test
    public void getJournalTagsRefusesANonWotEvent() {
        final ThingDeleted nonWotEvent =
                ThingDeleted.of(ThingId.of("serializer:thing"), 1L, TIMESTAMP, DittoHeaders.empty(), null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.getJournalTags(nonWotEvent))
                .withMessage("Unable to toJournal a non-'WotValidationConfigEvent' object! Was: " +
                        ThingDeleted.class);
    }

    @Test
    public void toJournalJsonRefusesANonWotEvent() {
        final ThingDeleted nonWotEvent =
                ThingDeleted.of(ThingId.of("serializer:thing"), 1L, TIMESTAMP, DittoHeaders.empty(), null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.toJournalJson(nonWotEvent))
                .withMessage("Expected WotValidationConfigEvent but got: " + ThingDeleted.class);
    }

    @Test
    public void toJournalJsonOfAWotEventCarriesTheEventType() {
        final WotValidationConfigCreated event =
                WotValidationConfigCreated.of(CONFIG_ID, config(), 1L, TIMESTAMP, DittoHeaders.empty(), null);

        assertThat(underTest.toJournalJson(event).getValue("type"))
                .map(value -> value.asString())
                .contains(WotValidationConfigCreated.TYPE);
    }

    private static WotValidationConfigEvent<?> wotEventOfType(final String type) {
        final WotValidationConfigEvent<?> event = mock(WotValidationConfigEvent.class);
        when(event.getType()).thenReturn(type);
        return event;
    }

    private static WotValidationConfig config() {
        return WotValidationConfig.of(CONFIG_ID, true, false,
                ThingValidationConfig.of(ThingValidationEnforceConfig.of(true, true, true, true, true),
                        ThingValidationForbidConfig.of(false, true, false, true)),
                FeatureValidationConfig.of(
                        FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true),
                        FeatureValidationForbidConfig.of(false, true, false, true, false, true)),
                Collections.emptyList(), WotValidationConfigRevision.of(1L), TIMESTAMP, TIMESTAMP, false, null);
    }

}
