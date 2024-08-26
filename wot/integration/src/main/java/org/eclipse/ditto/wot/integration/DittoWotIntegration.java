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
package org.eclipse.ditto.wot.integration;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Extension;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.generator.WotThingDescriptionGenerator;
import org.eclipse.ditto.wot.api.generator.WotThingModelExtensionResolver;
import org.eclipse.ditto.wot.api.generator.WotThingSkeletonGenerator;
import org.eclipse.ditto.wot.api.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.api.validator.WotThingModelValidator;

/**
 * Extension providing access to all Ditto WoT integration capabilities.
 *
 * @since 3.6.0
 */
public interface DittoWotIntegration extends Extension {

    /**
     * @return the applied WoT configuration.
     */
    WotConfig getWotConfig();

    /**
     * @return the WoT ThingModel fetcher used to download/fetch TMs from URLs.
     */
    WotThingModelFetcher getWotThingModelFetcher();

    /**
     * @return the WoT ThingModel resolver which fetches ThingModels and in addition resolves extensions
     * and reference to other ThingModels.
     */
    WotThingModelResolver getWotThingModelResolver();

    /**
     * @return the WoT ThingDescription generator which generates ThingDescriptions based on ThingModels.
     */
    WotThingDescriptionGenerator getWotThingDescriptionGenerator();

    /**
     * @return the WoT ThingModel extension and reference resolver used to resolve {@code tm:extends} and
     * {@code tm:ref} constructs in ThingModels.
     */
    WotThingModelExtensionResolver getWotThingModelExtensionResolver();

    /**
     * @return the WoT Thing skeleton generator which generates a JSON skeleton when creating new things
     * or features based on a ThingModel, adhering to default values of the model.
     */
    WotThingSkeletonGenerator getWotThingSkeletonGenerator();

    /**
     * @return the WoT ThingModel validator which can validate and enforce Ditto Thing payloads based on the
     * defined ThingModel and its JsonSchema for properties, actions, events.
     */
    WotThingModelValidator getWotThingModelValidator();

    /**
     * Get the {@code DittoWotIntegration} for an actor system.
     *
     * @param system the actor system.
     * @return the {@code DittoWotIntegration} extension.
     */
    static DittoWotIntegration get(final ActorSystem system) {
        return DefaultDittoWotIntegration.ExtensionId.INSTANCE.get(system);
    }
}
