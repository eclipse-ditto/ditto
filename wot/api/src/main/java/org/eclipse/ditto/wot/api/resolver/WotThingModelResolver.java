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
package org.eclipse.ditto.wot.api.resolver;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.generator.WotThingModelExtensionResolver;
import org.eclipse.ditto.wot.api.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Fetches WoT (Web of Things) ThingModels from {@code IRI}s/{@code URL}s, resolves extensions and references and
 * caches the fully resolved ThingModel in order to not always resolve extensions and references.
 *
 * @since 3.6.0
 */
public interface WotThingModelResolver {

    /**
     * Fetches the ThingModel resource at the passed {@code iri} and resolves extensions and references, returning the
     * fully resolved model.
     *
     * @param iri the IRI (URL) from which to fetch the ThingModel.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return a CompletionStage containing the fetched ThingModel or completed exceptionally with a
     * {@link org.eclipse.ditto.wot.model.WotThingModelInvalidException} if the fetched ThingModel could not be
     * parsed/interpreted as correct WoT ThingModel.
     * @throws org.eclipse.ditto.wot.model.ThingDefinitionInvalidException if the passed {@code iri} did not contain a
     * valid URL.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if the ThingModel could not be
     * fetched at the given {@code iri}.
     */
    CompletionStage<ThingModel> resolveThingModel(IRI iri, DittoHeaders dittoHeaders);

    /**
     * Fetches the ThingModel resource at the passed {@code url} and resolves extensions and references, returning the
     * fully resolved model.
     *
     * @param url the URL from which to fetch the ThingModel.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return a CompletionStage containing the fetched ThingModel or completed exceptionally with a
     * {@link org.eclipse.ditto.wot.model.WotThingModelInvalidException} if the fetched ThingModel could not be
     * parsed/interpreted as correct WoT ThingModel.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if the ThingModel could not be
     * fetched at the given {@code url}.
     */
    CompletionStage<ThingModel> resolveThingModel(URL url, DittoHeaders dittoHeaders);

    /**
     * Fetches all submodels contained in the passed {@code thingModel}, including extensions and references, returning
     * a Map of all submodels.
     *
     * @param thingModel the ThingModel to fetch submodels for.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return a CompletionStage containing the fetched ThingModel submodels or completed exceptionally with a
     * {@link org.eclipse.ditto.wot.model.WotThingModelInvalidException} if the fetched ThingModels could not be
     * parsed/interpreted as correct WoT ThingModels.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if one of the ThingModel submodels
     * could not be fetched at its defined {@code url}.
     */
    CompletionStage<Map<ThingSubmodel, ThingModel>> resolveThingModelSubmodels(ThingModel thingModel,
            DittoHeaders dittoHeaders);

    /**
     * Creates a new instance of WotThingModelResolver with the given {@code actorSystem} and {@code wotConfig}.
     *
     * @param wotConfig the WoT Config to use for creating the resolver.
     * @param thingModelFetcher the WoT ThingModel fetcher used to download/fetch TMs from URLs.
     * @param thingModelExtensionResolver the WoT ThingModel extension and reference resolver used to resolve
     * {@code tm:extends} and {@code tm:ref} constructs in ThingModels.
     * @param cacheLoaderExecutor the executor to use to run async cache loading tasks.
     * @return the created WotThingModelResolver.
     */
    static WotThingModelResolver of(final WotConfig wotConfig,
            final WotThingModelFetcher thingModelFetcher,
            final WotThingModelExtensionResolver thingModelExtensionResolver,
            final Executor cacheLoaderExecutor) {
        return new DefaultWotThingModelResolver(wotConfig, thingModelFetcher, thingModelExtensionResolver,
                cacheLoaderExecutor);
    }
}
