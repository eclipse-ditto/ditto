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

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.wot.model.ThingSkeleton;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

/**
 * @since 3.6.0
 */
public interface WotThingModelValidation {

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThing(ThingSkeleton<?> thingSkeleton,
            Thing thing,
            DittoHeaders dittoHeaders);

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeature(ThingSkeleton<?> thingSkeleton,
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
