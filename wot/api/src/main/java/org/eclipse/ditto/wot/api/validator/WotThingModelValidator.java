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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
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
    CompletionStage<Void> validateThing(Thing thing, DittoHeaders dittoHeaders);

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateThing(ThingModel thingModel, Thing thing, DittoHeaders dittoHeaders);

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeature(Feature feature, DittoHeaders dittoHeaders);

    /**
     * TODO TJ doc
     */
    CompletionStage<Void> validateFeature(ThingModel thingModel, Feature feature, DittoHeaders dittoHeaders);

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
