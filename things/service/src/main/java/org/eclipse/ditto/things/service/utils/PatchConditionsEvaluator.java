/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.utils;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;

/**
 * Utility class for evaluating patch conditions in Thing operations.
 *
 * <p>Provides methods to evaluate RQL-based conditions against existing Things
 * and filter payloads accordingly.</p>
 *
 * @since 3.8.0
 */
@Immutable
public final class PatchConditionsEvaluator {

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    private PatchConditionsEvaluator() {
        throw new AssertionError();
    }

    /**
     * Result of patch condition evaluation that indicates whether the payload became empty.
     */
    public record PatchConditionResult(JsonValue filteredValue, boolean empty) {

        public static PatchConditionResult of(final JsonValue value) {
                final boolean isEmpty = value.isObject() && value.asObject().isEmpty();
                return new PatchConditionResult(value, isEmpty);
        }
    }


    /**
     * Evaluates patch conditions for a {@link MergeThing} command and returns the filtered payload
     * together with a flag indicating if it became empty.
     */
    public static PatchConditionResult evaluatePatchConditionsWithResult(final Thing existingThing,
            final JsonValue mergeValue,
            final MergeThing command
    ) {
        final JsonValue filtered = evaluatePatchConditions(existingThing, mergeValue, command);
        return PatchConditionResult.of(filtered);
    }

    /**
     * Evaluates patch conditions for a {@link MergeThing} command and returns the filtered payload.
     */
    public static JsonValue evaluatePatchConditions(final Thing existingThing,
            final JsonValue mergeValue,
            final MergeThing command
    ) {
        final var patchConditionsOpt = command.getPatchConditions();

        if (patchConditionsOpt.isEmpty()) {
            return mergeValue;
        }

        if (!mergeValue.isObject()) {
            return mergeValue;
        }

        final Map<JsonPointer, String> patchConditions = patchConditionsOpt.get();
        return filterByConditions(
                existingThing,
                mergeValue.asObject(),
                patchConditions,
                command.getDittoHeaders(),
                false
        );
    }

    /**
     * Evaluates patch conditions for a migration payload and returns the filtered payload.
     */
    public static JsonObject evaluatePatchConditions(final Thing existingThing,
            final JsonObject migrationPayload,
            final Map<ResourceKey, String> patchConditions,
            final DittoHeaders dittoHeaders) {

        if (patchConditions.isEmpty() || migrationPayload.isEmpty()) {
            return migrationPayload;
        }

        final var pointerMapBuilder = new java.util.LinkedHashMap<JsonPointer, String>(patchConditions.size());
        for (final var e : patchConditions.entrySet()) {
            pointerMapBuilder.put(JsonFactory.newPointer(e.getKey().getResourcePath()), e.getValue());
        }

        return filterByConditions(
                existingThing,
                migrationPayload,
                pointerMapBuilder,
                dittoHeaders,
                true
        ).asObject();
    }

    private static JsonValue filterByConditions(final Thing existingThing,
            final JsonObject originalPayload,
            final Map<JsonPointer, String> patchConditions,
            final DittoHeaders headers,
            final boolean removeEmptyObjects
    ) {
        if (patchConditions.isEmpty() || originalPayload.isEmpty()) {
            return originalPayload;
        }

        JsonObjectBuilder maybeBuilder = null;
        boolean modified = false;

        for (final var entry : patchConditions.entrySet()) {
            final JsonPointer path = entry.getKey();
            final String conditionExpr = entry.getValue();

            if (originalPayload.getValue(path).isEmpty()) {
                continue;
            }

            final boolean matches = evaluateCondition(existingThing, conditionExpr, headers);
            if (!matches) {
                if (maybeBuilder == null) {
                    maybeBuilder = originalPayload.toBuilder();
                }
                maybeBuilder.remove(path);
                modified = true;
            }
        }

        final JsonValue result = modified ? maybeBuilder.build() : originalPayload;

        if (removeEmptyObjects && result.isObject()) {
            return removeEmptyObjectsRecursively(result.asObject());
        }
        return result;
    }


    private static boolean evaluateCondition(final Thing existingThing,
            final String conditionExpression,
            final DittoHeaders dittoHeaders) {
        try {
            final Criteria criteria = QueryFilterCriteriaFactory
                    .modelBased(RqlPredicateParser.getInstance())
                    .filterCriteria(conditionExpression, dittoHeaders);

            final var predicate = ThingPredicateVisitor.apply(criteria,
                    PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()));

            return predicate.test(existingThing);
        } catch (final Exception e) {
            throw InvalidRqlExpressionException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }


    /**
     * Recursively removes empty JSON objects from a JsonObject.
     * An object is considered empty if it has no fields.
     * Literal null values are preserved as they indicate explicit deletion intent.
     * If all nested objects are empty, the entire structure is reduced to an empty object.
     */
    public static JsonValue removeEmptyObjectsRecursively(final JsonObject jsonObject) {
        if (jsonObject.isEmpty()) {
            return JsonFactory.nullLiteral();
        }

        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        boolean hasAnyNonEmptyField = false;

        for (final var field : jsonObject) {
            final String key = field.getKeyName();
            final JsonValue value = field.getValue();

            if (value.isNull()) {
                builder.set(key, value);
                hasAnyNonEmptyField = true;
            } else if (value.isObject()) {
                final JsonValue cleaned = removeEmptyObjectsRecursively(value.asObject());
                if (!cleaned.isNull()) {
                    builder.set(key, cleaned);
                    hasAnyNonEmptyField = true;
                }
            } else {
                builder.set(key, value);
                hasAnyNonEmptyField = true;
            }
        }

        return hasAnyNonEmptyField ? builder.build() : JsonFactory.nullLiteral();
    }
}