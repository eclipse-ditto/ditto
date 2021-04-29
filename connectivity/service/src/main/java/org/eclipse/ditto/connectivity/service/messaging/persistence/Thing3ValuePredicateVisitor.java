/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.rql.query.things.ExistsThingPredicateVisitor;
import org.eclipse.ditto.rql.query.things.FilterThingPredicateVisitor;
import org.eclipse.ditto.rql.query.things.ThingPredicatePredicateVisitor;
import org.eclipse.ditto.things.model.Thing;

/**
 * Visitor to evaluate a criteria against a partial thing with unknown fields.
 */
final class Thing3ValuePredicateVisitor implements CriteriaVisitor<Function<Thing, Trilean>> {

    private final Set<JsonPointer> unknownFields;

    private Thing3ValuePredicateVisitor(final Set<JsonPointer> unknownFields) {
        // only internally instantiable
        this.unknownFields = unknownFields;
    }

    /**
     * Evaluate criteria against a partial thing to see whether it could be true.
     *
     * @param criteria the criteria.
     * @param unknownFields the set of unknown fields that shall not falsify the criteria evaluation result.
     * @param partialThing the partial thing.
     * @return whether the criteria may evaluate to true after replacing 'ignoredFields' by unknown values in
     * 'partialThing'.
     */
    static boolean couldBeTrue(final Criteria criteria, final Set<JsonPointer> unknownFields,
            final Thing partialThing) {
        return Trilean.FALSE != criteria.accept(new Thing3ValuePredicateVisitor(unknownFields)).apply(partialThing);
    }

    @Override
    public Function<Thing, Trilean> visitAnd(final List<Function<Thing, Trilean>> conjuncts) {
        return thing -> conjuncts.stream().map(f -> f.apply(thing)).reduce(Trilean::and).orElse(Trilean.TRUE);
    }

    @Override
    public Function<Thing, Trilean> visitAny() {
        return thing -> Trilean.TRUE;
    }

    @Override
    public Function<Thing, Trilean> visitExists(final ExistsFieldExpression fieldExpression) {
        return thing -> isUnknownField(fieldExpression)
                ? Trilean.UNKNOWN
                : Trilean.lift(ExistsThingPredicateVisitor.apply(fieldExpression).test(thing));
    }

    @Override
    public Function<Thing, Trilean> visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return thing -> {
            if (isUnknownField(fieldExpression)) {
                return Trilean.UNKNOWN;
            } else {
                final ThingPredicatePredicateVisitor ppVisitor = ThingPredicatePredicateVisitor.getInstance();
                return Trilean.lift(
                        FilterThingPredicateVisitor.apply(fieldExpression, predicate.accept(ppVisitor)).test(thing)
                );
            }
        };
    }

    @Override
    public Function<Thing, Trilean> visitNor(final List<Function<Thing, Trilean>> negativeDisjoints) {
        return visitOr(negativeDisjoints).andThen(Trilean::not);
    }

    @Override
    public Function<Thing, Trilean> visitOr(final List<Function<Thing, Trilean>> disjoints) {
        return thing -> disjoints.stream().map(f -> f.apply(thing)).reduce(Trilean::or).orElse(Trilean.FALSE);
    }

    private boolean isUnknownField(final FieldExpression fieldExpression) {
        return unknownFields.contains(fieldExpression.accept(new GetJsonPointer()));
    }

    private static final class GetJsonPointer implements FieldExpressionVisitor<JsonPointer> {

        @Override
        public JsonPointer visitAttribute(final String key) {
            return Thing.JsonFields.ATTRIBUTES.getPointer().append(JsonPointer.of(key));
        }

        @Override
        public JsonPointer visitFeature(final String featureId) {
            return Thing.JsonFields.FEATURES.getPointer().addLeaf(JsonKey.of(featureId));
        }

        @Override
        public JsonPointer visitFeatureProperties(final CharSequence featureId) {
            return visitFeature(featureId.toString()).append(JsonPointer.of("properties"));
        }

        @Override
        public JsonPointer visitFeatureDesiredProperties(final CharSequence featureId) {
            return visitFeature(featureId.toString()).append(JsonPointer.of("desiredProperties"));
        }

        @Override
        public JsonPointer visitFeatureIdProperty(final String featureId, final String property) {
            return visitFeature(featureId).append(JsonPointer.of(property));
        }

        @Override
        public JsonPointer visitFeatureIdDesiredProperty(final CharSequence featureId,
                final CharSequence desiredProperty) {

            return visitFeature(featureId.toString()).append(JsonPointer.of(desiredProperty));
        }

        @Override
        public JsonPointer visitSimple(final String fieldName) {
            return JsonPointer.empty().addLeaf(JsonKey.of(fieldName));
        }

    }

}
