/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration.generator;

import java.net.URL;
import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.wot.integration.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.ThingModel;

import akka.actor.ActorSystem;

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
    Optional<Thing> generateThingSkeleton(ThingId thingId,
            ThingModel thingModel,
            URL thingModelUrl,
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
    Optional<Feature> generateFeatureSkeleton(String featureId,
            ThingModel thingModel,
            URL thingModelUrl,
            DittoHeaders dittoHeaders);

    /**
     * Creates a new instance of WotThingSkeletonGenerator with the given {@code wotConfig}.
     *
     * @param actorSystem the actor system to use.
     * @param thingModelFetcher the ThingModel fetcher to fetch linked other ThingModels during the generation process.
     * @return the created WotThingSkeletonGenerator.
     */
    static WotThingSkeletonGenerator of(final ActorSystem actorSystem, final WotThingModelFetcher thingModelFetcher) {
        return new DefaultWotThingSkeletonGenerator(actorSystem, thingModelFetcher);
    }
}
