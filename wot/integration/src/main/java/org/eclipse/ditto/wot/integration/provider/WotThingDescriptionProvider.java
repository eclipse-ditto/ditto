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
package org.eclipse.ditto.wot.integration.provider;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.wot.model.ThingDescription;

import akka.actor.ActorSystem;
import akka.actor.Extension;

/**
 * Extension for providing WoT (Web of Things) {@link ThingDescription}s for given {@code thingId}s from either a
 * {@link ThingDefinition} or a {@link FeatureDefinition} from which the URL to a WoT
 * {@link org.eclipse.ditto.wot.model.ThingModel} is resolved and the {@link ThingDescription} is provided.
 *
 * @since 2.4.0
 */
@Immutable
public interface WotThingDescriptionProvider extends Extension {

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
    ThingDescription provideThingTD(@Nullable ThingDefinition thingDefinition,
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
    ThingDescription provideFeatureTD(ThingId thingId,
            @Nullable Thing thing,
            Feature feature,
            DittoHeaders dittoHeaders);

    /**
     * Provides a {@link Thing} skeleton for the given {@code thingId} using the passed {@code thingDefinition} to
     * extract a ThingModel URL from, fetching the ThingModel and generating the skeleton via
     * {@link org.eclipse.ditto.wot.integration.generator.WotThingSkeletonGenerator}.
     * The implementation should not throw exceptions, but return an empty optional if something went wrong during
     * fetching or generation of the skeleton.
     *
     * @param thingId the ThingId to generate the Thing skeleton for.
     * @param thingDefinition the ThingDefinition to resolve the ThingModel URL from.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return an optional Thing skeleton or empty optional if something went wrong during the skeleton creation.
     */
    Optional<Thing> provideThingSkeletonForCreation(ThingId thingId,
            @Nullable ThingDefinition thingDefinition,
            DittoHeaders dittoHeaders);

    /**
     * Provides a {@link Feature} skeleton for the given {@code featureId} using the passed {@code featureDefinition} to
     * extract a ThingModel URL from, fetching the ThingModel and generating the skeleton via
     * {@link org.eclipse.ditto.wot.integration.generator.WotThingSkeletonGenerator}.
     * The implementation should not throw exceptions, but return an empty optional if something went wrong during
     * fetching or generation of the skeleton.
     *
     * @param featureId the FeatureId to generate the Feature skeleton for.
     * @param featureDefinition the FeatureDefinition to resolve the ThingModel URL from.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return an optional Feature skeleton or empty optional if something went wrong during the skeleton creation.
     */
    Optional<Feature> provideFeatureSkeletonForCreation(String featureId,
            @Nullable FeatureDefinition featureDefinition,
            DittoHeaders dittoHeaders);

    /**
     * Get the {@code WotThingDescriptionProvider} for an actor system.
     *
     * @param system the actor system.
     * @return the {@code WotThingDescriptionProvider} extension.
     */
    static WotThingDescriptionProvider get(final ActorSystem system) {
        return DefaultWotThingDescriptionProvider.ExtensionId.INSTANCE.get(system);
    }
}
