/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import static org.eclipse.ditto.model.thingsearch.SearchFilter.Type;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.model.query.criteria.visitors.PredicateVisitor;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.visitors.FieldExpressionVisitor;

/**
 * Construct filter template from criteria.
 */
// TODO: generate this from RqlParser somehow?
final class ToStringCriteriaVisitor implements CriteriaVisitor<Stream<String>>,
        FieldExpressionVisitor<String>,
        PredicateVisitor<Function<String, Stream<String>>> {

    @Override
    public Stream<String> visitAnd(final Stream<Stream<String>> conjuncts) {
        return join(conjuncts, Type.AND.getName());
    }

    @Override
    public Stream<String> visitNor(final Stream<Stream<String>> negativeDisjoints) {
        return join(Stream.of(visitOr(negativeDisjoints)), Type.NOT.getName());
    }

    @Override
    public Stream<String> visitOr(final Stream<Stream<String>> disjoints) {
        return join(disjoints, Type.OR.getName());
    }

    @Override
    public Stream<String> visitAny() {
        return Stream.empty();
    }

    @Override
    public Stream<String> visitExists(final ExistsFieldExpression fieldExpression) {
        return join(Stream.of(Stream.of(fieldExpression.accept(this))), Type.EXISTS.getName());
    }

    @Override
    public Stream<String> visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return predicate.accept(this).apply(fieldExpression.accept(this));
    }

    @Override
    public String visitAttribute(final String key) {
        return "/attributes/" + key;
    }

    @Override
    public String visitFeature(final String featureId) {
        return "/features/" + featureId;
    }

    @Override
    public String visitFeatureIdProperty(final String featureId, final String property) {
        return "/features/" + featureId + "/properties/" + property;
    }

    @Override
    public String visitSimple(final String fieldName) {
        return fieldName;
    }

    @Override
    public Function<String, Stream<String>> visitEq(final Object value) {
        return joinPredicate(Type.EQ.getName(), value);
    }

    @Override
    public Function<String, Stream<String>> visitGe(final Object value) {
        return joinPredicate(Type.GE.getName(), value);
    }

    @Override
    public Function<String, Stream<String>> visitGt(final Object value) {
        return joinPredicate(Type.GT.getName(), value);
    }

    @Override
    public Function<String, Stream<String>> visitIn(final List<?> values) {
        return joinPredicate(Type.IN.getName(), values.stream().map(Object::toString));
    }

    @Override
    public Function<String, Stream<String>> visitLe(final Object value) {
        return joinPredicate(Type.LE.getName(), value);
    }

    @Override
    public Function<String, Stream<String>> visitLike(final String value) {
        return joinPredicate(Type.LIKE.getName(), value);
    }

    @Override
    public Function<String, Stream<String>> visitLt(final Object value) {
        return joinPredicate(Type.LT.getName(), value);
    }

    @Override
    public Function<String, Stream<String>> visitNe(final Object value) {
        return joinPredicate(Type.NE.getName(), value);
    }

    private static Function<String, Stream<String>> joinPredicate(final String opName, final Object value) {
        return joinPredicate(opName, Stream.of(value.toString()));
    }

    private static Function<String, Stream<String>> joinPredicate(final String opName, final Stream<String> operands) {
        return s -> join(Stream.of(Stream.of(s), operands), opName);
    }

    private static Stream<String> join(final Stream<Stream<String>> parts, final String opname) {
        return Stream.concat(Stream.of(opname, "("),
                Stream.concat(parts.flatMap(subParts -> Stream.concat(Stream.of(","), subParts)).skip(1L),
                        Stream.of(")")));
    }

    static String concatCriteria(final Stream<String> part1, @Nullable final String part2) {
        final Stream<String> toJoin = part2 == null
                ? part1
                : join(Stream.of(part1, Stream.of(part2)), Type.AND.getName());
        return toJoin.collect(Collectors.joining());
    }
}
