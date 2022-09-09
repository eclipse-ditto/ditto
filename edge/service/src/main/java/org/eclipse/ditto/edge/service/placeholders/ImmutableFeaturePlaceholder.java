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
package org.eclipse.ditto.edge.service.placeholders;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder implementation that replaces {@code feature:id}. The input value is a String and must be a
 * valid Feature ID.
 *
 * @since 1.5.0
 */
@Immutable
final class ImmutableFeaturePlaceholder implements FeaturePlaceholder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImmutableFeaturePlaceholder.class);
    private static final String ID_PLACEHOLDER = "id";

    private static final List<String> SUPPORTED = Collections.singletonList(ID_PLACEHOLDER);

    /**
     * Singleton instance of the {@code ImmutableFeaturePlaceholder}.
     */
    static final ImmutableFeaturePlaceholder INSTANCE = new ImmutableFeaturePlaceholder();

    @Override
    public List<String> resolveValues(final Signal<?> signal, final String placeholder) {
        checkNotNull(signal, "signal");
        argumentNotEmpty(placeholder, "placeholder");
        if (ID_PLACEHOLDER.equals(placeholder)) {
            return resolveIdPlaceholder(signal);
        }
        return List.of();
    }

    private static List<String> resolveIdPlaceholder(final Signal<?> signal) {
        final List<String> featureIds;
        if (signal instanceof WithFeatureId withFeatureId) {
            featureIds = Collections.singletonList((withFeatureId).getFeatureId());
        } else if (signal instanceof ThingModifyCommand || signal instanceof ThingModifiedEvent ||
                signal instanceof ThingModifyCommandResponse) {
            featureIds = ((WithOptionalEntity) signal).getEntity()
                    .map(value -> resolveFeatureIds(signal.getResourcePath(), value))
                    .orElseGet(List::of);
        } else {
            featureIds = List.of();
        }
        return featureIds;
    }

    private static List<String> resolveFeatureIds(final JsonPointer path, final JsonValue value) {
        final List<String> featureIds;
        if (path.isEmpty()) {
            // Signal is related to the full thing. We can expect value to be a Thing JSON
            if (value.isObject()) {
                final Thing thing = ThingsModelFactory.newThing(value.asObject());
                featureIds = thing.getFeatures()
                        .map(features -> features.stream().map(Feature::getId).toList())
                        .orElseGet(List::of);
            } else {
                LOGGER.info("Signal had empty path but non-object value. Can't resolve placeholder <feature:id>.");
                featureIds = List.of();
            }
        } else if (path.get(0).map(JsonKey::toString).filter("features"::equals).isEmpty()) {
            // Signal is not related to features therefore stop resolving.
            featureIds = List.of();
        } else if (path.getLevelCount() > 1) {
            // Signal is not related to a specific feature therefore use this feature ID.
            featureIds = path.get(1).map(JsonKey::toString)
                    .stream()
                    .toList();
        } else {
            // Signal is related to features. Therefore use all of the modified feature IDs.
            if (value.isObject()) {
                featureIds = ThingsModelFactory.newFeatures(value.asObject())
                        .stream()
                        .map(Feature::getId)
                        .toList();
            } else {
                featureIds = List.of();
            }
        }
        return featureIds;
    }

    @Override
    public String getPrefix() {
        return "feature";
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED.contains(name);
    }

}
