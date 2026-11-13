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
package org.eclipse.ditto.rql.query.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.rql.query.criteria.visitors.PredicateVisitor;
import org.junit.Test;

/**
 * Verifies that {@link LikePredicateImpl#accept(PredicateVisitor)} and
 * {@link ILikePredicateImpl#accept(PredicateVisitor)} delegate to the new
 * {@code visitLikeWithWildcards}/{@code visitILikeWithWildcards} default methods introduced on
 * {@link PredicateVisitor}, and that:
 * <ul>
 *     <li>a visitor which does NOT override the new default methods receives the exact same regular
 *     expression that {@code visitLike}/{@code visitILike} received before this change (byte-identical,
 *     golden-string proven) &mdash; this is the binary/behavioral compatibility guarantee existing visitors
 *     (e.g. Mongo's {@code CreateBsonPredicateVisitor}) rely on;</li>
 *     <li>a visitor which DOES override the new default methods receives the raw, not yet converted,
 *     wildcard expression verbatim.</li>
 * </ul>
 * <p>
 * The expected regular expressions below are HARDCODED literals, independently derived from
 * {@code org.eclipse.ditto.base.model.common.LikeHelper#convertToRegexSyntax(String)}'s documented
 * conversion semantics (whole-string quoting via {@code Pattern.quote}, with {@code *} and {@code ?}
 * breaking out of the quoted section, and anchors prepended/appended) and confirmed against the actual
 * runtime output of that method. They must NOT be computed by calling {@code LikeHelper} from this test,
 * otherwise the test would merely restate the production code and could never catch a regression in it.
 */
public final class LikeWithWildcardsDelegationTest {

    /**
     * Representative wildcard expressions (including escaped literals and edge cases), mapped to their
     * hardcoded golden regular expression as produced by {@code LikeHelper.convertToRegexSyntax(...)}.
     */
    private static final Map<String, String> WILDCARD_EXPRESSION_TO_EXPECTED_REGEX = new LinkedHashMap<>();

    static {
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("*abc", "^\\Q\\E.*\\Qabc\\E$");
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("abc*", "^\\Qabc\\E.*\\Q\\E$");
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("a*b?c", "^\\Qa\\E.*\\Qb\\E.\\Qc\\E$");
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("\\*", "^\\Q\\\\E.*\\Q\\E$");
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("%", "^\\Q%\\E$");
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("_", "^\\Q_\\E$");
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("\\", "^\\Q\\\\E$");
        WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.put("", "^\\Q\\E$");
    }

    @Test
    public void nonOverridingVisitorReceivesIdenticalRegexForLike() {
        for (final Map.Entry<String, String> entry : WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.entrySet()) {
            final String wildcardExpression = entry.getKey();
            final String expectedRegex = entry.getValue();
            final RecordingPredicateVisitor visitor = new RecordingPredicateVisitor();
            final Predicate predicate = new LikePredicateImpl(wildcardExpression);

            predicate.accept(visitor);

            assertThat(visitor.likeRegexSeen)
                    .as("visitLike must receive the hardcoded golden regex for wildcard expression [%s]",
                            wildcardExpression)
                    .isEqualTo(expectedRegex);
            assertThat(visitor.ilikeRegexSeen).isNull();
        }
    }

    @Test
    public void nonOverridingVisitorReceivesIdenticalRegexForILike() {
        for (final Map.Entry<String, String> entry : WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.entrySet()) {
            final String wildcardExpression = entry.getKey();
            final String expectedRegex = entry.getValue();
            final RecordingPredicateVisitor visitor = new RecordingPredicateVisitor();
            final Predicate predicate = new ILikePredicateImpl(wildcardExpression);

            predicate.accept(visitor);

            assertThat(visitor.ilikeRegexSeen)
                    .as("visitILike must receive the hardcoded golden regex for wildcard expression [%s]",
                            wildcardExpression)
                    .isEqualTo(expectedRegex);
            assertThat(visitor.likeRegexSeen).isNull();
        }
    }

    @Test
    public void nonOverridingVisitorReceivesNullForNullValueLike() {
        final RecordingPredicateVisitor visitor = new RecordingPredicateVisitor();
        final Predicate predicate = new LikePredicateImpl(null);

        predicate.accept(visitor);

        assertThat(visitor.likeRegexSeen).isNull();
        assertThat(visitor.likeInvoked).isTrue();
    }

    @Test
    public void nonOverridingVisitorReceivesNullForNullValueILike() {
        final RecordingPredicateVisitor visitor = new RecordingPredicateVisitor();
        final Predicate predicate = new ILikePredicateImpl(null);

        predicate.accept(visitor);

        assertThat(visitor.ilikeRegexSeen).isNull();
        assertThat(visitor.ilikeInvoked).isTrue();
    }

    @Test
    public void overridingVisitorReceivesRawWildcardExpressionForLike() {
        for (final String wildcardExpression : WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.keySet()) {
            final RawCapturingPredicateVisitor visitor = new RawCapturingPredicateVisitor();
            final Predicate predicate = new LikePredicateImpl(wildcardExpression);

            predicate.accept(visitor);

            assertThat(visitor.rawLikeWildcardSeen)
                    .as("overriding visitor must receive the raw wildcard expression verbatim for [%s]",
                            wildcardExpression)
                    .isEqualTo(wildcardExpression);
            // Must NOT have been routed through visitLike at all.
            assertThat(visitor.likeInvoked).isFalse();
        }
    }

    @Test
    public void overridingVisitorReceivesRawWildcardExpressionForILike() {
        for (final String wildcardExpression : WILDCARD_EXPRESSION_TO_EXPECTED_REGEX.keySet()) {
            final RawCapturingPredicateVisitor visitor = new RawCapturingPredicateVisitor();
            final Predicate predicate = new ILikePredicateImpl(wildcardExpression);

            predicate.accept(visitor);

            assertThat(visitor.rawILikeWildcardSeen)
                    .as("overriding visitor must receive the raw wildcard expression verbatim for [%s]",
                            wildcardExpression)
                    .isEqualTo(wildcardExpression);
            assertThat(visitor.ilikeInvoked).isFalse();
        }
    }

    @Test
    public void overridingVisitorReceivesNullRawWildcardExpression() {
        final RawCapturingPredicateVisitor likeVisitor = new RawCapturingPredicateVisitor();
        new LikePredicateImpl(null).accept(likeVisitor);
        assertThat(likeVisitor.rawLikeWildcardSeen).isNull();

        final RawCapturingPredicateVisitor ilikeVisitor = new RawCapturingPredicateVisitor();
        new ILikePredicateImpl(null).accept(ilikeVisitor);
        assertThat(ilikeVisitor.rawILikeWildcardSeen).isNull();
    }

    /**
     * A {@link PredicateVisitor} that does NOT override {@code visitLikeWithWildcards}/
     * {@code visitILikeWithWildcards}, so it exercises the default-method delegation and records exactly
     * what {@code visitLike}/{@code visitILike} received (i.e. what every pre-existing visitor implementation
     * would still receive).
     */
    private static final class RecordingPredicateVisitor implements PredicateVisitor<Void> {

        @Nullable private String likeRegexSeen;
        @Nullable private String ilikeRegexSeen;
        private boolean likeInvoked;
        private boolean ilikeInvoked;

        @Override
        public Void visitEq(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitGe(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitGt(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitLe(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitLt(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitNe(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitLike(@Nullable final String value) {
            likeInvoked = true;
            likeRegexSeen = value;
            return null;
        }

        @Override
        public Void visitILike(@Nullable final String value) {
            ilikeInvoked = true;
            ilikeRegexSeen = value;
            return null;
        }

        @Override
        public Void visitIn(final java.util.List<?> values) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A {@link PredicateVisitor} that DOES override {@code visitLikeWithWildcards}/
     * {@code visitILikeWithWildcards} to capture the raw wildcard expression, bypassing regex conversion
     * entirely (as a new SQL-backend visitor would).
     */
    private static final class RawCapturingPredicateVisitor implements PredicateVisitor<Void> {

        @Nullable private String rawLikeWildcardSeen;
        @Nullable private String rawILikeWildcardSeen;
        private boolean likeInvoked;
        private boolean ilikeInvoked;

        @Override
        public Void visitEq(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitGe(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitGt(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitLe(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitLt(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitNe(@Nullable final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Void visitLike(@Nullable final String value) {
            likeInvoked = true;
            return null;
        }

        @Override
        public Void visitILike(@Nullable final String value) {
            ilikeInvoked = true;
            return null;
        }

        @Override
        public Void visitLikeWithWildcards(@Nullable final String wildcardExpression) {
            rawLikeWildcardSeen = wildcardExpression;
            return null;
        }

        @Override
        public Void visitILikeWithWildcards(@Nullable final String wildcardExpression) {
            rawILikeWildcardSeen = wildcardExpression;
            return null;
        }

        @Override
        public Void visitIn(final java.util.List<?> values) {
            throw new UnsupportedOperationException();
        }
    }
}
