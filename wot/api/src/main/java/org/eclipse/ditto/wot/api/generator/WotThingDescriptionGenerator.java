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
package org.eclipse.ditto.wot.api.generator;

import java.net.URL;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.ThingDescription;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Generator for WoT (Web of Things) {@link ThingDescription} based on a given WoT {@link ThingModel} and context of the
 * Ditto {@link Thing} to generate the ThingDescription for.
 *
 * @since 2.4.0
 */
public interface WotThingDescriptionGenerator {

    /**
     * Provides a {@link ThingDescription} for the given {@code thingDefinition} and {@code thingId} combination.
     *
     * @param thingDefinition the ThingDefinition to extract the URL from a {@code ThingModel} from to use as template
     * for the provided {@link ThingDescription}.
     * @param thingId the ThingId to provide the ThingDescription for.
     * @param thing the optional Thing to use to resolve placeholders and metadata from.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return the ThingDescription.
     * @throws org.eclipse.ditto.wot.model.ThingDefinitionInvalidException if the passed in {@code thingDefinition} was
     * {@code null} or did not contain a ThingModel URL.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if the ThingModel could not be accessed/
     * downloaded.
     */
    CompletionStage<ThingDescription> provideThingTD(@Nullable ThingDefinition thingDefinition,
            ThingId thingId,
            @Nullable Thing thing,
            DittoHeaders dittoHeaders);

    /**
     * Provides a {@link ThingDescription} for the given {@code thingDefinition}, {@code thingId} and
     * {@code featureId} combination.
     *
     * @param thingId the ThingId to provide the ThingDescription for.
     * @param thing the optional Thing to use to resolve placeholders and metadata from.
     * @param feature the Feature to provide the ThingDescription for.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return the ThingDescription.
     * @throws org.eclipse.ditto.wot.model.ThingDefinitionInvalidException if the passed in {@code featureDefinition} was
     * {@code null} or did not contain a ThingModel URL.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if the ThingModel could not be accessed/
     * downloaded.
     */
    CompletionStage<ThingDescription> provideFeatureTD(ThingId thingId,
            @Nullable Thing thing,
            Feature feature,
            DittoHeaders dittoHeaders);

    /**
     * Generates a ThingDescription for the given {@code thingId}, optionally using the passed {@code thing} to lookup
     * thing specific placeholders.
     * Uses the passed in {@code thingModel} and generates TD forms, security definition etc. in order to make it a
     * valid TD.
     *
     * @param thingId the ThingId to generate the ThingDescription for.
     * @param thing the optional Thing from which to resolve metadata from.
     * @param placeholderLookupObject the optional JsonObject to dynamically resolve placeholders from
     * (e.g. a Thing or Feature).
     * @param featureId the optional feature name if the TD should be generated for a certain feature of the Thing.
     * @param thingModel the ThingModel to use as template for generating the TD.
     * @param thingModelUrl the URL from which the ThingModel was fetched.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeException which might occur during the
     * generation.
     * @return the generated ThingDescription for the given {@code thingId} based on the passed in {@code thingModel}.
     * @throws org.eclipse.ditto.wot.model.WotThingModelInvalidException if the WoT ThingModel did not contain the
     * mandatory {@code "@type"} being {@code "tm:ThingModel"}
     */
    CompletionStage<ThingDescription> generateThingDescription(ThingId thingId,
            @Nullable Thing thing,
            @Nullable JsonObject placeholderLookupObject,
            @Nullable String featureId,
            ThingModel thingModel,
            URL thingModelUrl,
            DittoHeaders dittoHeaders);

    /**
     * Creates a new instance of WotThingDescriptionGenerator with the given {@code wotConfig}.
     *
     * @param wotConfig the WoTConfig to use for creating the generator.
     * @param thingModelResolver the ThingModel resolver to fetch and resolve (extensions, refs) of linked other
     * ThingModels during the generation process.
     * @param executor the executor to use to run async tasks.
     * @return the created WotThingDescriptionGenerator.
     */
    static WotThingDescriptionGenerator of(final WotConfig wotConfig,
            final WotThingModelResolver thingModelResolver,
            final Executor executor) {
        return new DefaultWotThingDescriptionGenerator(wotConfig, thingModelResolver, executor);
    }
}
