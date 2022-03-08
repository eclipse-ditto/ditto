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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Resolver for WoT (Web of Things) ThingModels - resolves "extensions" {@code tm:extends} and "references"
 * {@code tm:ref} in ThingModels by fetching referenced models and merging their contents together into a resulting TM.
 *
 * @since 2.4.0
 */
public interface WotThingModelExtensionResolver {

    /**
     * Resolves the "extension" links ({@code tm:extends}) contained in the passed {@code thingModel} and merges them
     * into the returned ThingModel.
     *
     * @param thingModel the ThingModel to resolve extensions in.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return a new ThingModel with resolved extensions based on the passed in model.
     * @throws org.eclipse.ditto.wot.model.ThingDefinitionInvalidException if a contained extended ThingModel did not
     * contain a valid URL.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if an extended WoT ThingModel could not
     * be fetched from its configured resource.
     * @throws org.eclipse.ditto.wot.model.WotThingModelInvalidException if the fetched extended ThingModel could not be
     * parsed/interpreted as correct WoT ThingModel.
     */
    ThingModel resolveThingModelExtensions(ThingModel thingModel, DittoHeaders dittoHeaders);

    /**
     * Resolves the "references" ({@code tm:ref}) contained in the passed {@code thingModel} and merges them into the
     * returned ThingModel.
     *
     * @param thingModel the ThingModel to resolve references in.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return a new ThingModel with resolved references based on the passed in model.
     * @throws org.eclipse.ditto.wot.model.ThingDefinitionInvalidException if a contained referenced ThingModel did not
     * contain a valid URL.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if a referenced WoT ThingModel could not
     * be fetched from its configured resource.
     * @throws org.eclipse.ditto.wot.model.WotThingModelInvalidException if the fetched referenced ThingModel could not
     * be parsed/interpreted as correct WoT ThingModel.
     */
    ThingModel resolveThingModelRefs(ThingModel thingModel, DittoHeaders dittoHeaders);
}
