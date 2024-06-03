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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
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
            Thing thing,
            DittoHeaders dittoHeaders);

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeaturesProperties(Map<String, ThingModel> featureThingModels,
            Features features,
            DittoHeaders dittoHeaders);

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeatureProperties(ThingModel featureThingModel,
            Feature feature,
            DittoHeaders dittoHeaders);

    /**
     * TODO TJ doc
     * @return
     */
    static WotThingModelValidation createInstance(final TmValidationConfig validationConfig) {
        return new DefaultWotThingModelValidation(validationConfig);
    }
}
