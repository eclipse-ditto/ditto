/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Metadata;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Unit Tests for {@link org.eclipse.ditto.model.things.MetadataBuilder}.
 */
public class MetadataBuilderTest extends AbstractStrategyTest {

    @Test
    public void appliesEventCorrectly() {
        final FeaturePropertiesModified event =
            FeaturePropertiesModified.of(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, REVISION,
                DittoHeaders.of(Collections.singletonMap("ditto-metadata:issuedAt", "Hallo")));

        ThingMetadataFactory metadataBuilder = new ThingMetadataFactory();

        Metadata metadata = metadataBuilder.buildFromEvent(event, THING);

        assertThat(metadata)
            .hasToString("{\"features\":{\"flux-capacitor\":{\"properties\":{\"bumlux\":{\"issuedAt\":\"Hallo\"}}}}}");
    }

    @Test
    public void takesGivenIssuedAtIfNoneInHeader() {
        final FeaturePropertiesModified event =
            FeaturePropertiesModified.of(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, REVISION,
                DittoHeaders.empty());

        ThingMetadataFactory metadataBuilder = new ThingMetadataFactory();

        Metadata metadata = metadataBuilder.buildFromEvent(event, THING, "Hallo");

        assertThat(metadata)
            .hasToString("{\"features\":{\"flux-capacitor\":{\"properties\":{\"bumlux\":{\"issuedAt\":\"Hallo\"}}}}}");
    }

    @Test
    public void appliesNested() {
        final FeaturePropertiesModified event =
            FeaturePropertiesModified.of(THING_ID, FEATURE_ID, FeatureProperties.newBuilder()
                .set(FEATURE_PROPERTY_POINTER, FEATURE_PROPERTY_VALUE)
                .set(JsonPointer.of("dimmer/position"), 25)
                .build(), REVISION,
                DittoHeaders.of(Collections.singletonMap("ditto-metadata:issuedAt", "Hallo")));

        ThingMetadataFactory metadataBuilder = new ThingMetadataFactory();

        Metadata metadata = metadataBuilder.buildFromEvent(event, THING);

        assertThat(metadata)
            .hasToString("{\"features\":{\"flux-capacitor\":{\"properties\":{\"bumlux\":{\"issuedAt\":\"Hallo\"},\"dimmer\":{\"position\":{\"issuedAt\":\"Hallo\"}}}}}}");
    }
}