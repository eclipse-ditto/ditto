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
package org.eclipse.ditto.wot.api.validator;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * TODO TJ doc
 * @since 3.6.0
 */
public interface WotThingModelValidator {

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThing(Thing thing,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThing(@Nullable ThingDefinition thingDefinition,
            Thing thing,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThing(ThingModel thingModel,
            Thing thing,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThingDefinitionModification(ThingDefinition thingDefinition,
            Thing thing,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThingAttributes(@Nullable ThingDefinition thingDefinition,
            @Nullable Attributes attributes,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThingAttributes(ThingModel thingModel,
            @Nullable Attributes attributes,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     * @param thingDefinition
     * @param attributePointer
     * @param attributeValue
     * @param resourcePath
     * @param dittoHeaders
     * @return
     */
    CompletionStage<Void> validateThingAttribute(@Nullable ThingDefinition thingDefinition,
            JsonPointer attributePointer,
            JsonValue attributeValue,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeatures(@Nullable ThingDefinition thingDefinition,
            Features features,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeatures(ThingModel thingModel,
            Features features,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeature(@Nullable ThingDefinition thingDefinition,
            Feature feature,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeature(@Nullable ThingModel thingModel,
            @Nullable ThingModel featureThingModel,
            Feature feature,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeatureDefinitionModification(FeatureDefinition featureDefinition,
            Feature feature,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeatureProperties(@Nullable FeatureDefinition featureDefinition,
            String featureId,
            @Nullable FeatureProperties properties,
            boolean desiredProperties,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeatureProperties(ThingModel featureThingModel,
            String featureId,
            @Nullable FeatureProperties properties,
            boolean desiredProperties,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeatureProperty(@Nullable FeatureDefinition featureDefinition,
            String featureId,
            JsonPointer propertyPointer,
            JsonValue propertyValue,
            boolean desiredProperty,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Creates a new instance of WotThingModelValidator with the given {@code wotConfig}.
     *
     * @param wotConfig the WoT config to use.
     * @param thingModelResolver the ThingModel resolver to fetch and resolve (extensions, refs) of linked other
     * ThingModels during the generation process.
     * @param executor the executor to use to run async tasks.
     * @return the created WotThingModelValidator.
     */
    static WotThingModelValidator of(final WotConfig wotConfig,
            final WotThingModelResolver thingModelResolver,
            final Executor executor) {
        return new DefaultWotThingModelValidator(wotConfig, thingModelResolver, executor);
    }
}
