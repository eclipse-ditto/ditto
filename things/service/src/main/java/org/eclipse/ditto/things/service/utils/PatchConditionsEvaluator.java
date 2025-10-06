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
 * <p>
 * This class provides methods to evaluate RQL-based conditions against existing Things
 * and filter payloads based on those conditions. It eliminates code duplication between
 * different command strategies that need to handle patch conditions.
 * </p>
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
     * Evaluates patch conditions for a MergeThing command and filters the merge value accordingly.
     * This method applies RQL conditions to filter out parts of the merge payload
     * that should not be applied based on the current state of the Thing.
     * 
     * <p>
     * The patch conditions are provided as a JSON object where each key represents
     * a JSON pointer path and each value is an RQL expression. If the RQL expression
     * evaluates to {@code false} against the existing Thing, the corresponding part
     * of the merge payload at that path will be removed.
     * </p>
     *
     * @param existingThing the current state of the Thing
     * @param mergeValue the original merge value to be filtered
     * @param command the MergeThing command containing patch conditions
     * @return the filtered merge value with parts removed based on failed conditions
     */
    public static JsonValue evaluatePatchConditions(final Thing existingThing,
            final JsonValue mergeValue,
            final MergeThing command) {
        
        final var patchConditionsOpt = command.getPatchConditions();
        if (patchConditionsOpt.isEmpty()) {
            return mergeValue;
        }

        if (!mergeValue.isObject()) {
            return mergeValue;
        }

        final JsonObject patchConditions = patchConditionsOpt.get();
        final JsonObject mergeObject = mergeValue.asObject();

        final JsonObjectBuilder adjustedPayloadBuilder = mergeObject.toBuilder();

        for (final var field : patchConditions) {
            final String conditionPath = field.getKeyName();
            final String conditionExpression = field.getValue().asString();

            final boolean conditionMatches = evaluateCondition(existingThing, conditionExpression, command.getDittoHeaders());
            final JsonPointer resourcePointer = JsonPointer.of(conditionPath);
            final boolean containsResource = mergeObject.getValue(resourcePointer).isPresent();

            if (!conditionMatches && containsResource) {
                adjustedPayloadBuilder.remove(resourcePointer);
            }
        }

        return adjustedPayloadBuilder.build();
    }

    /**
     * Evaluates patch conditions for a migration payload and filters it accordingly.
     * This method applies RQL conditions to filter out parts of the migration payload
     * that should not be applied based on the current state of the Thing.
     * 
     * <p>
     * The patch conditions are provided as a map where each key represents
     * a ResourceKey and each value is an RQL expression. If the RQL expression
     * evaluates to {@code false} against the existing Thing, the corresponding part
     * of the migration payload at that path will be removed.
     * </p>
     *
     * @param existingThing the current state of the Thing
     * @param migrationPayload the original migration payload to be filtered
     * @param patchConditions the map of resource keys to RQL condition expressions
     * @param dittoHeaders the Ditto headers for error reporting
     * @return the filtered migration payload with parts removed based on failed conditions
     */
    public static JsonObject evaluatePatchConditions(final Thing existingThing,
            final JsonObject migrationPayload,
            final Map<ResourceKey, String> patchConditions,
            final DittoHeaders dittoHeaders) {
        
        final JsonObjectBuilder adjustedPayloadBuilder = migrationPayload.toBuilder();

        for (final Map.Entry<ResourceKey, String> entry : patchConditions.entrySet()) {
            final ResourceKey resourceKey = entry.getKey();
            final String conditionExpression = entry.getValue();

            final boolean conditionMatches = evaluateCondition(existingThing, conditionExpression, dittoHeaders);

            final JsonPointer resourcePointer = JsonFactory.newPointer(resourceKey.getResourcePath());
                    if (!conditionMatches && containsResourceKey(migrationPayload, resourcePointer)) {
                adjustedPayloadBuilder.remove(resourcePointer);
            }
        }

        return adjustedPayloadBuilder.build();
    }

    /**
     * Evaluates a single RQL condition against an existing Thing.
     * 
     * @param existingThing the current state of the Thing
     * @param conditionExpression the RQL expression to evaluate
     * @param dittoHeaders the Ditto headers for error reporting
     * @return {@code true} if the condition matches, {@code false} otherwise
     * @throws InvalidRqlExpressionException if the RQL expression is invalid
     */
    public static boolean evaluateCondition(final Thing existingThing,
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

    private static boolean containsResourceKey(final JsonObject payload,
            final JsonPointer pointer) {
        return payload.getValue(pointer).isPresent();
    }
}
