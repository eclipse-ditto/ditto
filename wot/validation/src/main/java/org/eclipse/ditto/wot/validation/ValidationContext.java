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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.ThingDefinition;

/**
 * A validation context provides the context of an API call which can be used to dynamically determine custom
 * configuration overrides, e.g. based on specific {@link DittoHeaders} or specific thing or feature definitions.
 *
 * @param dittoHeaders the DittoHeaders of the API call
 * @param thingDefinition the optional ThingDefinition of the thing to be updated by the API call
 * @param featureDefinition the optional FeatureDefinition of the thing to be updated by the API call
 */
public record ValidationContext(
        DittoHeaders dittoHeaders,
        @Nullable ThingDefinition thingDefinition,
        @Nullable FeatureDefinition featureDefinition
) {

    public static ValidationContext buildValidationContext(final DittoHeaders dittoHeaders,
            @Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition
    ) {
        return new ValidationContext(dittoHeaders, thingDefinition, featureDefinition);
    }

    public static ValidationContext buildValidationContext(final DittoHeaders dittoHeaders,
            @Nullable final ThingDefinition thingDefinition
    ) {
        return new ValidationContext(dittoHeaders, thingDefinition, null);
    }

    public static ValidationContext buildValidationContext(final DittoHeaders dittoHeaders,
            @Nullable final FeatureDefinition featureDefinition
    ) {
        return new ValidationContext(dittoHeaders, null, featureDefinition);
    }
}
