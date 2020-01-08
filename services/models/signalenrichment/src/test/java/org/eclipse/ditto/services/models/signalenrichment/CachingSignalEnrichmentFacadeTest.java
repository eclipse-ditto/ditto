/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.signalenrichment;

import java.time.Duration;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.cache.config.DefaultCacheConfig;

import com.typesafe.config.ConfigFactory;

import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacade}.
 */
public final class CachingSignalEnrichmentFacadeTest extends AbstractSignalEnrichmentFacadeTest {

    @Override
    protected SignalEnrichmentFacade createSignalEnrichmentFacadeUnderTest(final TestKit kit,
            final Duration duration) {
        final CacheConfig cacheConfig = DefaultCacheConfig.of(ConfigFactory.parseString("foobar {}"), "foobar");
        return CachingSignalEnrichmentFacade.of(kit.getRef(), Duration.ofSeconds(10L), cacheConfig,
                        kit.getSystem().getDispatcher());
    }

    @Override
    protected JsonObject getSuccessPartialThingJson() {
        return JsonObject.of("{\n" +
                "  \"_revision\": 3,\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"attributes\": {\"x\":  5},\n" +
                "  \"features\": {\"y\": {\"properties\": {\"z\":  true}}}\n" +
                "}");
    }

    @Override
    protected JsonFieldSelector actualSelectedFields(final JsonFieldSelector selector) {
        return JsonFactory.newFieldSelectorBuilder()
                .addPointers(selector)
                .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                .build();
    }
}
