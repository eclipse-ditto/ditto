/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.common.LikeHelper;
import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SearchIndexingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.NamespaceSearchIndexConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;

import com.typesafe.config.Config;

/**
 * Default {@link SearchIndexingSignalEnrichmentFacadeProvider} who provides a {@link org.eclipse.ditto.internal.models.signalenrichment.SearchIndexingSignalEnrichmentFacade}.
 */
public final class SearchIndexingSignalEnrichmentFacadeProvider implements CachingSignalEnrichmentFacadeProvider {

    private static final List<JsonFieldDefinition<?>> REQUIRED_INDEXED_FIELDS = List.of(
            Thing.JsonFields.ID,
            Thing.JsonFields.POLICY_ID,
            Thing.JsonFields.NAMESPACE,
            Thing.JsonFields.REVISION);

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public SearchIndexingSignalEnrichmentFacadeProvider(final ActorSystem actorSystem, final Config config) {
        // No-Op but required for extension initialisation
    }

    @Override
    public CachingSignalEnrichmentFacade getSignalEnrichmentFacade(
            final ActorSystem actorSystem,
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        final SearchConfig searchConfig =
                DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));

        // Build a map of field selectors for the enrichment facade to use to quickly look up by Thing namespace.
        final List<Pair<Pattern, JsonFieldSelector>> namespaceAndFieldSelector = new ArrayList<>();

        for (final NamespaceSearchIndexConfig namespaceConfig : searchConfig.getNamespaceIndexedFields()) {

            if (!namespaceConfig.getIndexedFields().isEmpty()) {

                // Ensure the constructed JsonFieldSelector has the required fields needed for the search to work.
                final Set<String> set = new LinkedHashSet<>();
                set.addAll(REQUIRED_INDEXED_FIELDS.stream()
                        .map(JsonFieldDefinition::getPointer)
                        .map(JsonPointer::toString)
                        .toList());
                set.addAll(namespaceConfig.getIndexedFields());

                final List<String> searchIncludeFields = new ArrayList<>(set);

                final JsonFieldSelector indexedFields =
                        JsonFactory.newFieldSelector(searchIncludeFields, JsonParseOptions.newBuilder().build());

                // Build a Pattern from the namespace value.
                final Pattern namespacePattern = Pattern.compile(
                        Objects.requireNonNull(LikeHelper.convertToRegexSyntax(namespaceConfig.getNamespacePattern())));

                namespaceAndFieldSelector.add(Pair.create(namespacePattern, indexedFields));
            }
        }

        return SearchIndexingSignalEnrichmentFacade.newInstance(
                namespaceAndFieldSelector,
                cacheLoaderFacade,
                cacheConfig,
                cacheLoaderExecutor,
                cacheNamePrefix);
    }
}
