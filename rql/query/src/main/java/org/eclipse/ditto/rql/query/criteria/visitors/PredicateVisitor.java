/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.rql.query.criteria.visitors;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.LikeHelper;
import org.eclipse.ditto.rql.query.criteria.Predicate;

/**
 * Compositional interpreter of {@link Predicate}.
 */
public interface PredicateVisitor<T> {

    T visitEq(@Nullable Object value);

    T visitGe(@Nullable Object value);

    T visitGt(@Nullable Object value);

    T visitLe(@Nullable Object value);

    T visitLt(@Nullable Object value);

    T visitNe(@Nullable Object value);

    T visitLike(@Nullable String value);

    T visitILike(@Nullable String value);

    T visitIn(List<?> values);

    /**
     * Visits a "like" predicate passing the original, not yet converted, wildcard expression
     * (using {@code *} and {@code ?} wildcards) instead of the regular expression derived from it.
     * The default implementation preserves the previous behavior by converting the passed
     * {@code wildcardExpression} to a regular expression via {@link LikeHelper#convertToRegexSyntax(String)}
     * and delegating to {@link #visitLike(String)}.
     *
     * @param wildcardExpression the original wildcard expression, or {@code null}.
     * @return the result of visiting the "like" predicate.
     * @since 4.0.0
     */
    default T visitLikeWithWildcards(@Nullable final String wildcardExpression) {
        return visitLike(LikeHelper.convertToRegexSyntax(wildcardExpression));
    }

    /**
     * Visits an "ilike" predicate passing the original, not yet converted, wildcard expression
     * (using {@code *} and {@code ?} wildcards) instead of the regular expression derived from it.
     * The default implementation preserves the previous behavior by converting the passed
     * {@code wildcardExpression} to a regular expression via {@link LikeHelper#convertToRegexSyntax(String)}
     * and delegating to {@link #visitILike(String)}.
     *
     * @param wildcardExpression the original wildcard expression, or {@code null}.
     * @return the result of visiting the "ilike" predicate.
     * @since 4.0.0
     */
    default T visitILikeWithWildcards(@Nullable final String wildcardExpression) {
        return visitILike(LikeHelper.convertToRegexSyntax(wildcardExpression));
    }

}
