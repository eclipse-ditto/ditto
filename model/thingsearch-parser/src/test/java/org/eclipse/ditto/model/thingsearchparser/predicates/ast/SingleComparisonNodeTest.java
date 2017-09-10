/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.thingsearchparser.predicates.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link SingleComparisonNode}.
 */
public class SingleComparisonNodeTest {

    /** */
    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(SingleComparisonNode.class) //
                .usingGetClass() //
                .suppress(Warning.REFERENCE_EQUALITY) //
                .verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void typeConstructorWithNullAsFilterProperty() {
        new SingleComparisonNode(SingleComparisonNode.Type.eq, null, "test");
    }

    /** */
    @Test
    public void typeConstructorSuccess() {
        final SingleComparisonNode SingleComparisonNode =
                new SingleComparisonNode(
                        org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode.Type.eq,
                        "propertyName", "propertyValue");
        assertThat(SingleComparisonNode.getComparisonType()).isEqualTo(
                org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode.Type.eq);
        assertThat(SingleComparisonNode.getComparisonProperty()).isEqualTo("propertyName");
        assertThat(SingleComparisonNode.getComparisonValue()).isEqualTo("propertyValue");
    }

    /** */
    @Test
    public void typeConstructorWithNullAsValue() {
        final SingleComparisonNode SingleComparisonNode = new SingleComparisonNode(
                org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode.Type.eq, "propertyName",
                null);
        assertThat(SingleComparisonNode.getComparisonType()).isEqualTo(
                org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode.Type.eq);
        assertThat(SingleComparisonNode.getComparisonProperty()).isEqualTo("propertyName");
        assertThat(SingleComparisonNode.getComparisonValue()).isNull();
    }

    @Test
    public void visitorGetsVisited() {
        final PredicateVisitor visitorMock = mock(PredicateVisitor.class);
        final SingleComparisonNode SingleComparisonNode =
                new SingleComparisonNode(
                        org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode.Type.eq,
                        "propertyName", "propertyValue");
        SingleComparisonNode.accept(visitorMock);
        verify(visitorMock).visit(SingleComparisonNode);
    }

}
