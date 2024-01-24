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
package org.eclipse.ditto.internal.models.signalenrichment;

import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.json.JsonObject;

/**
 * Unit tests for {@link DittoCachingSignalEnrichmentFacade}.
 */
public final class DittoCachingSignalEnrichmentFacadeTest extends AbstractCachingSignalEnrichmentFacadeTest {

    private static final JsonObject EXPECTED_THING_JSON = JsonObject.of("""
            {
              "policyId": "policy:id",
              "attributes": {"x":  5},
              "features": {"y": {"properties": {"z":  true}}},
              "_metadata": {"attributes": {"x": {"type": "x attribute"}}}
            }""");

    @Override
    protected CachingSignalEnrichmentFacade createCachingSignalEnrichmentFacade(final TestKit kit,
            final ByRoundTripSignalEnrichmentFacade cacheLoaderFacade, final CacheConfig cacheConfig) {
        return DittoCachingSignalEnrichmentFacade.newInstance(
                cacheLoaderFacade,
                cacheConfig,
                kit.getSystem().getDispatcher(),
                "test");
    }

    @Override
    protected JsonObject getExpectedThingJson() {
        return EXPECTED_THING_JSON;
    }


}
