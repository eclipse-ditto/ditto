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
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Generator for WoT (Web of Things) based Ditto {@link Thing}s and {@link Feature} skeletons based on
 * a given WoT {@link org.eclipse.ditto.wot.model.ThingModel} for creation of Ditto entities (Things/Features).
 *
 * @since 2.4.0
 */
public interface WotThingSkeletonGenerator {

    /**
     * Generates a skeleton {@link Thing} for the given {@code thingId}.
     * Uses the passed in {@code thingModel} and generates
     * <ul>
     * <li>{@code attributes} on Thing level for all required properties in that TM</li>
     * <li>Features for all included TM submodels (links with {@code tm:submodel} type)</li>
     * <li>Feature properties on Feature levels for all required properties in linked submodel TMs</li>
     * </ul>
     *
     * @param thingId the ThingId to generate the Thing skeleton for.
     * @param thingModel the ThingModel to use as template for generating the Thing skeleton.
     * @param thingModelUrl the URL from which the ThingModel was fetched.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeException which might occur during the
     * generation.
     * @return the generated Thing skeleton for the given {@code thingId} based on the passed in {@code thingModel}.
     * @throws org.eclipse.ditto.wot.model.WotThingModelInvalidException if the WoT ThingModel did not contain the
     * mandatory {@code "@type"} being {@code "tm:ThingModel"}
     */
    default CompletionStage<Optional<Thing>> generateThingSkeleton(ThingId thingId,
            ThingModel thingModel,
            URL thingModelUrl,
            DittoHeaders dittoHeaders) {
        return generateThingSkeleton(thingId, thingModel, thingModelUrl, false, dittoHeaders);
    }

    /**
     * Provides a {@link Thing} skeleton for the given {@code thingId} using the passed {@code thingDefinition} to
     * extract a ThingModel URL from, fetching the ThingModel and generating the skeleton via
     * {@link WotThingSkeletonGenerator}.
     * The implementation should not throw exceptions, but return an empty optional if something went wrong during
     * fetching or generation of the skeleton.
     *
     * @param thingId the ThingId to generate the Thing skeleton for.
     * @param thingDefinition the ThingDefinition to resolve the ThingModel URL from.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return an optional Thing skeleton or empty optional if something went wrong during the skeleton creation.
     * @since 3.6.0
     */
    CompletionStage<Optional<Thing>> provideThingSkeletonForCreation(ThingId thingId,
            @Nullable ThingDefinition thingDefinition,
            DittoHeaders dittoHeaders);

    /**
     * Generates a skeleton {@link Thing} for the given {@code thingId}.
     * Uses the passed in {@code thingModel} and generates
     * <ul>
     * <li>{@code attributes} on Thing level for all required properties in that TM</li>
     * <li>Features for all included TM submodels (links with {@code tm:submodel} type)</li>
     * <li>Feature properties on Feature levels for all required properties in linked submodel TMs</li>
     * </ul>
     *
     * @param thingId the ThingId to generate the Thing skeleton for.
     * @param thingModel the ThingModel to use as template for generating the Thing skeleton.
     * @param thingModelUrl the URL from which the ThingModel was fetched.
     * @param generateDefaultsForOptionalProperties whether for optional marked properties in the WoT ThingModel
     * properties should be generated based on their defaults.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeException which might occur during the
     * generation.
     * @return the generated Thing skeleton for the given {@code thingId} based on the passed in {@code thingModel}.
     * @throws org.eclipse.ditto.wot.model.WotThingModelInvalidException if the WoT ThingModel did not contain the
     * mandatory {@code "@type"} being {@code "tm:ThingModel"}
     * @since 3.5.0
     */
    CompletionStage<Optional<Thing>> generateThingSkeleton(ThingId thingId,
            ThingModel thingModel,
            URL thingModelUrl,
            boolean generateDefaultsForOptionalProperties,
            DittoHeaders dittoHeaders);

    /**
     * Generates a skeleton {@link Feature} for the given {@code featureId}.
     * Uses the passed in {@code thingModel} and generates
     * <ul>
     * <li>{@code properties} for all required properties in that TM</li>
     * </ul>
     *
     * @param featureId the FeatureId to generate
     * @param thingModel the ThingModel to use as template for generating the Feature skeleton.
     * @param thingModelUrl the URL from which the ThingModel was fetched.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeException which might occur during the
     * generation.
     * @return the generated Feature skeleton for the given {@code featureId} based on the passed in {@code thingModel}.
     * @throws org.eclipse.ditto.wot.model.WotThingModelInvalidException if the WoT ThingModel did not contain the
     * mandatory {@code "@type"} being {@code "tm:ThingModel"}
     */
    default CompletionStage<Optional<Feature>> generateFeatureSkeleton(String featureId,
            ThingModel thingModel,
            URL thingModelUrl,
            DittoHeaders dittoHeaders) {
        return generateFeatureSkeleton(featureId, thingModel, thingModelUrl, false, dittoHeaders);
    }

    /**
     * Provides a {@link Feature} skeleton for the given {@code featureId} using the passed {@code featureDefinition} to
     * extract a ThingModel URL from, fetching the ThingModel and generating the skeleton via
     * {@link WotThingSkeletonGenerator}.
     * The implementation should not throw exceptions, but return an empty optional if something went wrong during
     * fetching or generation of the skeleton.
     *
     * @param featureId the FeatureId to generate the Feature skeleton for.
     * @param featureDefinition the FeatureDefinition to resolve the ThingModel URL from.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return an optional Feature skeleton or empty optional if something went wrong during the skeleton creation.
     * @since 3.6.0
     */
    CompletionStage<Optional<Feature>> provideFeatureSkeletonForCreation(String featureId,
            @Nullable FeatureDefinition featureDefinition,
            DittoHeaders dittoHeaders);

    /**
     * Generates a skeleton {@link Feature} for the given {@code featureId}.
     * Uses the passed in {@code thingModel} and generates
     * <ul>
     * <li>{@code properties} for all required properties in that TM</li>
     * </ul>
     *
     * @param featureId the FeatureId to generate
     * @param thingModel the ThingModel to use as template for generating the Feature skeleton.
     * @param thingModelUrl the URL from which the ThingModel was fetched.
     * @param generateDefaultsForOptionalProperties whether for optional marked properties in the WoT ThingModel
     * properties should be generated based on their defaults.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeException which might occur during the
     * generation.
     * @return the generated Feature skeleton for the given {@code featureId} based on the passed in {@code thingModel}.
     * @throws org.eclipse.ditto.wot.model.WotThingModelInvalidException if the WoT ThingModel did not contain the
     * mandatory {@code "@type"} being {@code "tm:ThingModel"}
     * @since 3.5.0
     */
    CompletionStage<Optional<Feature>> generateFeatureSkeleton(String featureId,
            ThingModel thingModel,
            URL thingModelUrl,
            boolean generateDefaultsForOptionalProperties,
            DittoHeaders dittoHeaders);

    /**
     * Creates a new instance of WotThingSkeletonGenerator with the given {@code wotConfig}.
     *
     * @param wotConfig the WoT Config to use for creating the generator.
     * @param thingModelResolver the ThingModel resolver to fetch and resolve (extensions, refs) of linked other
     * ThingModels during the generation process.
     * @param executor the executor to use to run async tasks.
     * @return the created WotThingSkeletonGenerator.
     */
    static WotThingSkeletonGenerator of(final WotConfig wotConfig,
            final WotThingModelResolver thingModelResolver,
            final Executor executor) {
        return new DefaultWotThingSkeletonGenerator(wotConfig, thingModelResolver, executor);
    }
}
