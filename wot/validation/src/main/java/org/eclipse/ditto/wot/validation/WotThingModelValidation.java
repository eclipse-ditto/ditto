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
package org.eclipse.ditto.wot.validation;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

/**
 * Provides functionality to validate specific parts of a Ditto {@link Thing} and/or Ditto Thing {@link Features} and
 * single {@link Feature} instances.
 *
 * @since 3.6.0
 */
public interface WotThingModelValidation {

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
     * @param thingModel
     * @param attributePointer
     * @param attributeValue
     * @param resourcePath
     * @param dittoHeaders
     * @return
     */
    CompletionStage<Void> validateThingAttribute(ThingModel thingModel,
            JsonPointer attributePointer,
            JsonValue attributeValue,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeaturesPresence(Map<String, ThingModel> featureThingModels,
            @Nullable Features features,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeaturesProperties(Map<String, ThingModel> featureThingModels,
            @Nullable Features features,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeaturePresence(Map<String, ThingModel> featureThingModels,
            Feature feature,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeature(ThingModel featureThingModel,
            Feature feature,
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
     * @param featureThingModel
     * @param featureId
     * @param propertyPointer
     * @param propertyValue
     * @param desiredProperty
     * @param resourcePath
     * @param dittoHeaders
     * @return
     */
    CompletionStage<Void> validateFeatureProperty(ThingModel featureThingModel,
            String featureId,
            JsonPointer propertyPointer,
            JsonValue propertyValue,
            boolean desiredProperty,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * TODO TJ doc
     * @return
     */
    static WotThingModelValidation createInstance(final TmValidationConfig validationConfig) {
        return new DefaultWotThingModelValidation(validationConfig);
    }
}
