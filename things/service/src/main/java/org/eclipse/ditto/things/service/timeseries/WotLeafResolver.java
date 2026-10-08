/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.timeseries;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.model.Ingest;
import org.eclipse.ditto.timeseries.model.WotTimeseriesAnnotation;
import org.eclipse.ditto.wot.model.DittoWotExtension;
import org.eclipse.ditto.wot.model.ObjectSchema;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Resolves a feature-property data path against a WoT submodel's typed schema, returning the
 * governing {@code ditto:timeseries} annotation and the leaf's declared unit (when present).
 * <p>
 * Path-to-property mapping mirrors {@code wot/validation/InternalValidation#findPropertyBasedOnPath}
 * (which is not public): the optional leading {@code <prefix>:category} segment is matched and
 * stripped, then nested object properties are descended via {@link ObjectSchema#getProperties()}.
 * The Ditto WoT extension prefix is taken from the model's {@code @context} via
 * {@code AtContext#determinePrefixFor} — the same way the skeleton generator, TD generator and
 * validator obtain it.
 */
final class WotLeafResolver {

    private static final String DEFAULT_DITTO_PREFIX = "ditto";

    private WotLeafResolver() {
        throw new AssertionError();
    }

    /**
     * Walks the submodel's WoT property schema along {@code propertyPath}. Returns a match when a
     * {@code ditto:timeseries} annotation with {@code ingest = ALL} sits on the leaf or on any
     * ancestor along the path (the deepest, most specific one wins). The reported unit is the
     * leaf schema's own {@code unit}, when declared.
     */
    static Optional<ResolvedLeaf> resolveLeaf(final ThingModel submodelTm,
            final JsonPointer propertyPath) {

        final Properties properties = submodelTm.getProperties().orElse(null);
        if (properties == null || propertyPath.isEmpty()) {
            return Optional.empty();
        }
        final String prefix = dittoExtensionPrefix(submodelTm);
        final String categoryKey = prefix + ":" + DittoWotExtension.DITTO_WOT_EXTENSION_CATEGORY;
        final String timeseriesKey = prefix + ":" + WotTimeseriesAnnotation.EXTENSION_LOCAL_NAME;

        final MatchedProperty matched = matchTopLevelProperty(properties, propertyPath, categoryKey);
        if (matched == null) {
            return Optional.empty();
        }
        SingleDataSchema schema = matched.schema();
        WotTimeseriesAnnotation found =
                WotTimeseriesAnnotation.findInProperty(schema.toJson(), timeseriesKey).orElse(null);
        JsonPointer remaining = matched.remaining();
        while (!remaining.isEmpty()) {
            final String segment = remaining.getRoot().map(Object::toString).orElse(null);
            final SingleDataSchema nested = (segment == null) ? null
                    : ObjectSchema.fromJson(schema.toJson()).getProperties().get(segment);
            if (nested == null) {
                schema = null;
                break;
            }
            schema = nested;
            final WotTimeseriesAnnotation deeper =
                    WotTimeseriesAnnotation.findInProperty(schema.toJson(), timeseriesKey).orElse(null);
            if (deeper != null) {
                found = deeper;
            }
            remaining = remaining.nextLevel();
        }
        if (found == null || found.getIngest() != Ingest.ALL) {
            return Optional.empty();
        }
        final String unit = (schema == null) ? null : schema.getUnit().orElse(null);
        return Optional.of(new ResolvedLeaf(found, unit));
    }

    @Nullable
    private static MatchedProperty matchTopLevelProperty(final Properties properties,
            final JsonPointer path, final String categoryKey) {

        final String first = path.getRoot().map(Object::toString).orElse(null);
        if (first == null) {
            return null;
        }
        // Categorised form: <category>/<name>/... — match when the named property declares
        // exactly this category.
        if (path.getLevelCount() >= 2) {
            final String second = path.get(1).map(Object::toString).orElse(null);
            final Property candidate = (second == null) ? null
                    : properties.getProperty(second).orElse(null);
            if (candidate != null && categoryOf(candidate, categoryKey).filter(first::equals).isPresent()) {
                return new MatchedProperty(candidate, path.getSubPointer(2).orElse(JsonPointer.empty()));
            }
        }
        // Plain form: <name>/... — only when the property declares no category.
        final Property candidate = properties.getProperty(first).orElse(null);
        if (candidate != null && categoryOf(candidate, categoryKey).isEmpty()) {
            return new MatchedProperty(candidate, path.getSubPointer(1).orElse(JsonPointer.empty()));
        }
        return null;
    }

    private static Optional<String> categoryOf(final Property property, final String categoryKey) {
        return property.toJson().getValue(categoryKey)
                .filter(JsonValue::isString)
                .map(JsonValue::asString);
    }

    private static String dittoExtensionPrefix(final ThingModel submodelTm) {
        try {
            return submodelTm.getAtContext()
                    .determinePrefixFor(DittoWotExtension.DITTO_WOT_EXTENSION)
                    .orElse(DEFAULT_DITTO_PREFIX);
        } catch (final RuntimeException e) {
            // A well-formed WoT TM always declares @context, but never let a malformed model
            // crash leaf resolution — fall back to the conventional prefix.
            return DEFAULT_DITTO_PREFIX;
        }
    }

    record ResolvedLeaf(WotTimeseriesAnnotation annotation, @Nullable String unit) {}

    private record MatchedProperty(SingleDataSchema schema, JsonPointer remaining) {}
}
