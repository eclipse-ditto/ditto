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
package org.eclipse.ditto.rql.parser;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.rql.model.ParsedPlaceholder;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.PredicateParser;
import org.eclipse.ditto.rql.model.predicates.ast.ExistsNode;
import org.eclipse.ditto.rql.model.predicates.ast.LogicalNode;
import org.eclipse.ditto.rql.model.predicates.ast.MultiComparisonNode;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.model.predicates.ast.SingleComparisonNode;
import org.junit.Test;

public class RqlPredicateParserTest {

    private final PredicateParser parser = RqlPredicateParser.getInstance();

    @Test
    public void testComparisonEqualsWithNumberValue() throws ParserException {
        final RootNode root = parser.parse("eq(username,123)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.EQ);
        assertThat(comparison.getComparisonProperty()).isEqualTo("username");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123L);
    }

    @Test
    public void testComparisonEqualsWithStringValue() throws ParserException {
        final RootNode root = parser.parse("eq(username,\"te\\\"st\")");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("te\"st");
    }

    @Test
    public void testComparisonEqualsWithStringSingleQuoteValue() throws ParserException {
        final RootNode root = parser.parse("eq(username,'te\\'st')");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("te'st");
    }

    @Test
    public void testComparisonEqualsWithStringValueContainingBackslash() throws ParserException {
        final RootNode root = parser.parse("eq(username,\"abc\\nyz\")");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("abc\nyz");
    }

    @Test
    public void testComparisonEqualsWithStringSingleQuoteValueContainingBackslash() throws ParserException {
        final RootNode root = parser.parse("eq(username,'abc\\nyz')");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("abc\nyz");
    }

    @Test(expected = ParserException.class)
    public void testWrongComparisonEqualsWithStringValue() throws ParserException {
        parser.parse("eq/(username,\"te\\\"st\")");
    }

    @Test(expected = ParserException.class)
    public void testWrongComparisonEqualsWithStringValue1() throws ParserException {
        parser.parse("eq(username;\"te\\\"st\")");
    }

    @Test
    public void testComparisonNotEqualsWithNumberValue() throws ParserException {
        final RootNode root = parser.parse("ne(username,123)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.NE);
        assertThat(comparison.getComparisonProperty()).isEqualTo("username");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123L);
    }

    @Test
    public void testComparisonGreaterThanWithNumberValue() throws ParserException {
        final RootNode root = parser.parse("gt(width,123)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.GT);
        assertThat(comparison.getComparisonProperty()).isEqualTo("width");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123L);
    }

    @Test
    public void testComparisonGreaterOrEqualsWithNumberValue() throws ParserException {
        final RootNode root = parser.parse("ge(width,123)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.GE);
        assertThat(comparison.getComparisonProperty()).isEqualTo("width");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123L);
    }

    @Test
    public void testComparisonLowerThanWithNumberValue() throws ParserException {
        final RootNode root = parser.parse("lt(width,123)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.LT);
        assertThat(comparison.getComparisonProperty()).isEqualTo("width");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123L);
    }

    @Test
    public void testComparisonLowerOrEqualsWithNumberValue() throws ParserException {
        final RootNode root = parser.parse("le(width,123)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.LE);
        assertThat(comparison.getComparisonProperty()).isEqualTo("width");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123L);
    }

    // like operator fail for Numbers
    @Test(expected = ParserException.class)
    public void testComparisonLikeWithNumberValue() throws ParserException {
        parser.parse("like(width,123*)");
    }

    // Like operator work for Strings
    @Test
    public void testComparisonLikeWithStringValue() throws ParserException {
        final RootNode root = parser.parse("like(width,\"test*\")");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.LIKE);
        assertThat(comparison.getComparisonProperty()).isEqualTo("width");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("test*");
    }

    @Test
    public void testComparisonLikeWithStringSingleQuoteValue() throws ParserException {
        final RootNode root = parser.parse("like(width,'test*')");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.LIKE);
        assertThat(comparison.getComparisonProperty()).isEqualTo("width");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("test*");
    }

    @Test
    public void testComparisonIn() throws ParserException {
        final RootNode root = parser.parse("in(attributes,\"test\",1,true)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final MultiComparisonNode comparison = (MultiComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(MultiComparisonNode.Type.IN);
        assertThat(comparison.getComparisonProperty()).isEqualTo("attributes");
        assertThat(comparison.getComparisonValue().size()).isEqualTo(3);
        assertThat(comparison.getComparisonValue().get(0)).isEqualTo("test");
        assertThat(comparison.getComparisonValue().get(1)).isEqualTo(1L);
        assertThat(comparison.getComparisonValue().get(2)).isEqualTo(true);
    }

    @Test
    public void letsParse() throws ParserException {
        SingleComparisonNode comparison;

        final RootNode root = parser.parse("and(eq(username,123),eq(coolness,\"super\"),or(eq(username,854)))");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final LogicalNode and = (LogicalNode) root.getChildren().get(0);
        assertThat(and.getName()).isEqualTo("and");
        assertThat(and.getChildren().size()).isEqualTo(3);

        comparison = (SingleComparisonNode) and.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.EQ);
        assertThat(comparison.getComparisonProperty()).isEqualTo("username");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123L);

        comparison = (SingleComparisonNode) and.getChildren().get(1);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.EQ);
        assertThat(comparison.getComparisonProperty()).isEqualTo("coolness");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("super");

        final LogicalNode or = (LogicalNode) and.getChildren().get(2);
        assertThat(or.getName()).isEqualTo("or");
        assertThat(or.getChildren().size()).isEqualTo(1);

        comparison = (SingleComparisonNode) or.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.EQ);
        assertThat(comparison.getComparisonProperty()).isEqualTo("username");
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(854L);
    }

    @Test
    public void testAnd() throws ParserException {
        final RootNode root = parser.parse("and(eq(username,123),eq(coolness,\"super\"),eq(username,854))");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final LogicalNode and = (LogicalNode) root.getChildren().get(0);
        assertThat(and.getName()).isEqualTo("and");
        assertThat(and.getType()).isEqualTo(LogicalNode.Type.AND);
        assertThat(and.getChildren().size()).isEqualTo(3);
    }

    @Test
    public void testOr() throws ParserException {
        final RootNode root = parser.parse("or(eq(username,123),eq(coolness,\"super\"),eq(username,854))");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final LogicalNode and = (LogicalNode) root.getChildren().get(0);
        assertThat(and.getName()).isEqualTo("or");
        assertThat(and.getType()).isEqualTo(LogicalNode.Type.OR);
        assertThat(and.getChildren().size()).isEqualTo(3);
    }

    @Test
    public void testNot() throws ParserException {
        final RootNode root = parser.parse("not(eq(username,123))");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final LogicalNode and = (LogicalNode) root.getChildren().get(0);
        assertThat(and.getName()).isEqualTo("not");
        assertThat(and.getType()).isEqualTo(LogicalNode.Type.NOT);
        assertThat(and.getChildren().size()).isEqualTo(1);
    }

    @Test(expected = ParserException.class)
    public void testNotWithMoreThanOneSubQueries() throws ParserException {
        parser.parse("not(eq(username,123),eq(coolness,\"super\"),eq(username,854))");
    }

    @Test
    public void checkWithEmptyString() throws ParserException {
        final RootNode root = parser.parse("eq(username,\" \")");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(" ");
    }

    @Test
    public void checkWithEmptyStringSingleQuote() throws ParserException {
        final RootNode root = parser.parse("eq(username,' ')");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(" ");
    }

    @Test(expected = ParserException.class)
    public void emptyString() throws ParserException {
        parser.parse("");
    }

    @Test(expected = ParserException.class)
    public void invalidOperator() throws ParserException {
        parser.parse("or(ASDF(username,123))");
    }

    @Test(expected = ParserException.class)
    public void invalidNumber() throws ParserException {
        parser.parse("eq(username, 123())");
    }

    @Test(expected = ParserException.class)
    public void invalidDouble() throws ParserException {
        parser.parse("eq(username, 123.0())");
    }

    @Test(expected = ParserException.class)
    public void tooManyParanthesesPredicate() throws ParserException {
        parser.parse("eq(coolness,\"super\"))");
    }

    @Test(expected = ParserException.class)
    public void noContentAfterComma() throws ParserException {
        parser.parse("and(eq(username, 123), )");
    }

    @Test(expected = ParserException.class)
    public void noContentBeforeComma() throws ParserException {
        parser.parse("and(   ,eq(username, 123))");
    }

    @Test(expected = ParserException.class)
    public void characterAfterParameter() throws ParserException {
        parser.parse("and(eq(username, 123)xx,eq(coolness,\"super\"))");
    }

    @Test(expected = ParserException.class)
    public void noEnd() throws ParserException {
        parser.parse("eq(username, 123");
    }

    @Test(expected = ParserException.class)
    public void noQuery() throws ParserException {
        parser.parse("(username, 123)");
    }

    @Test(expected = ParserException.class)
    public void wrongName() throws ParserException {
        parser.parse("eaq(username, 123)");
    }

    @Test(expected = ParserException.class)
    public void missingQuotesInStrings() throws ParserException {
        parser.parse("eq(username,test)");
    }

    @Test
    public void trueBooleanValueAsContent() throws ParserException {
        final RootNode root = parser.parse("eq(username,true)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Boolean.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(true);
    }


    @Test
    public void falseBooleanValueAsContent() throws ParserException {
        final RootNode root = parser.parse("eq(username,false)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Boolean.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(false);
    }


    @Test
    public void trueValueAsStringContent() throws ParserException {
        final RootNode root = parser.parse("eq(username,\"true\")");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparison.getComparisonValue()).isEqualTo("true");
    }

    @Test
    public void negativeNumberValueAsContent() throws ParserException {
        final RootNode root = parser.parse("eq(username,-123)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(-123L);
    }

    @Test
    public void value0AsContent() throws ParserException {
        final RootNode root = parser.parse("eq(username,0)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Long.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(0L);
    }

    @Test(expected = ParserException.class)
    public void negativeValue0AsContent() throws ParserException {
        parser.parse("eq(username,-0)");
    }

    @Test(expected = ParserException.class)
    public void numberValueIsNotAllowedToStartWith0() throws ParserException {
        parser.parse("eq(username, 0123)");
    }

    @Test(expected = ParserException.class)
    public void negativeNumberValueIsNotAllowedToStartWith0() throws ParserException {
        parser.parse("eq(username, -0123)");
    }

    @Test(expected = ParserException.class)
    public void numberValueExceedsLimit() throws ParserException {
        parser.parse("eq(username,12356143287134097863590813406135981332472031847)");
    }

    @Test
    public void doubleValueAsContent() throws ParserException {
        final RootNode root = parser.parse("eq(username,123.7)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Double.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(123.7);
    }

    @Test
    public void negativeDoubleValueAsContent() throws ParserException {
        final RootNode root = parser.parse("eq(username,-123.7)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Double.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(-123.7);
    }

    @Test
    public void doubleValueAsContentWith0() throws ParserException {
        final RootNode root = parser.parse("eq(username,0.7)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Double.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(0.7);
    }

    @Test
    public void negativeDoubleValueAsContentWith0() throws ParserException {
        final RootNode root = parser.parse("eq(username,-0.7)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonValue().getClass()).isEqualTo(Double.class);
        assertThat(comparison.getComparisonValue()).isEqualTo(-0.7);
    }

    @Test(expected = ParserException.class)
    public void doubleValueNotAllowedToStartWith0AndNoPeriod() throws ParserException {
        parser.parse("eq(username,012.7)");
    }

    @Test(expected = ParserException.class)
    public void negativeDoubleValueNotAllowedToStartWith0AndNoPeriod() throws ParserException {
        parser.parse("eq(username,-012.7)");
    }

    @Test(expected = ParserException.class)
    public void negativeDoubleValueNotAllowedToStartWithPeriod() throws ParserException {
        parser.parse("eq(username,-.7)");
    }

    @Test(expected = ParserException.class)
    public void doubleQuotesInString() throws ParserException {
        parser.parse("eq(username,\"abc\"\")");
    }

    @Test(expected = ParserException.class)
    public void doubleValueNotAllowedToStartWithPeriod() throws ParserException {
        parser.parse("eq(username,.7)");
    }

    @Test(expected = ParserException.class)
    public void doubleValueNotAllowedToContainOnlyPeriod() throws ParserException {
        parser.parse("eq(username,.)");
    }

    @Test(expected = ParserException.class)
    public void doubleValueNotAllowedToContainMoreThanOnePeriod() throws ParserException {
        parser.parse("eq(username,12.7.8)");
    }

    @Test(expected = ParserException.class)
    public void doubleValueNotAllowedToEndWithPeriod() throws ParserException {
        parser.parse("eq(username,127.)");
    }

    @Test
    public void testSingleComparisonWithNullValue() throws ParserException {
        final RootNode root = parser.parse("eq(username,null)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparison = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(SingleComparisonNode.Type.EQ);
        assertThat(comparison.getComparisonProperty()).isEqualTo("username");
        assertThat(comparison.getComparisonValue()).isNull();
    }

    @Test
    public void testMultiComparisonWithNullValue() throws ParserException {
        final RootNode root = parser.parse("in(attributes,null,\"test\",null)");
        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final MultiComparisonNode comparison = (MultiComparisonNode) root.getChildren().get(0);
        assertThat(comparison.getComparisonType()).isEqualTo(MultiComparisonNode.Type.IN);
        assertThat(comparison.getComparisonProperty()).isEqualTo("attributes");
        assertThat(comparison.getComparisonValue().size()).isEqualTo(3);
        assertThat(comparison.getComparisonValue().get(0)).isNull();
        assertThat(comparison.getComparisonValue().get(1)).isEqualTo("test");
        assertThat(comparison.getComparisonValue().get(2)).isNull();
    }

    @Test
    public void testFieldExists() throws ParserException {
        final RootNode root = parser.parse("exists(features/scanner)");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final ExistsNode existsNode = (ExistsNode) root.getChildren().get(0);
        assertThat(existsNode.getProperty().getClass()).isEqualTo(String.class);
        assertThat(existsNode.getProperty()).isEqualTo("features/scanner");
    }

    @Test
    public void testComplexSpecialChars() throws ParserException {
        final String complexVal = "!#$%&'()*+,/:;=?@[\\\\]{|} äaZ0";
        final RootNode root = parser.parse("eq(attributes/complex,\"" + complexVal + "\")");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparisonNode = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparisonNode.getComparisonProperty()).isEqualTo("attributes/complex");
        assertThat(comparisonNode.getComparisonValue().getClass()).isEqualTo(String.class);
        assertThat(comparisonNode.getComparisonValue()).isEqualTo("!#$%&'()*+,/:;=?@[\\]{|} äaZ0");
    }

    @Test(expected = ParserException.class)
    public void testFieldExistsInvalidWithQuotes() throws ParserException {
        parser.parse("exists(\"features/scanner\")");
    }

    @Test(expected = ParserException.class)
    public void testFieldExistsInvalidWithMoreParams() throws ParserException {
        parser.parse("exists(features/scanner,\"test\")");
    }

    @Test
    public void testTopicActionExists() throws ParserException {
        final RootNode root = parser.parse("exists(topic:action)");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final ExistsNode existsNode = (ExistsNode) root.getChildren().get(0);
        assertThat(existsNode.getProperty().getClass()).isEqualTo(String.class);
        assertThat(existsNode.getProperty()).isEqualTo("topic:action");
    }

    @Test
    public void testMiscPlaceholderMayBeUsedAsValue() throws ParserException {
        final RootNode root = parser.parse("lt(_modified,time:now)");

        assertThat(root).isNotNull();
        assertThat(root.getChildren().size()).isEqualTo(1);

        final SingleComparisonNode comparisonNode = (SingleComparisonNode) root.getChildren().get(0);
        assertThat(comparisonNode.getComparisonProperty()).isEqualTo("_modified");
        assertThat(comparisonNode.getComparisonValue().getClass()).isEqualTo(ParsedPlaceholder.class);
        assertThat(comparisonNode.getComparisonValue()).isEqualTo(ParsedPlaceholder.of("time:now"));
        assertThat(comparisonNode.getComparisonValue()).isEqualTo("time:now");
    }

    @Test(expected = ParserException.class)
    public void testUnknownPlaceholderPrefixCannotBeUsed() throws ParserException {
        parser.parse("eq(thingId,foo:bar)");
    }

}
