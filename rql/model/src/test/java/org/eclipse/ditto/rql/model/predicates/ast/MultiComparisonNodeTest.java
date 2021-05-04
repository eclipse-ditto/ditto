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
package org.eclipse.ditto.rql.model.predicates.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link MultiComparisonNode}.
 */
public class MultiComparisonNodeTest {

    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(MultiComparisonNode.class)
                .usingGetClass()
                .suppress(Warning.REFERENCE_EQUALITY)
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void typeConstructorWithNullAsFilterProperty() {
        new MultiComparisonNode(MultiComparisonNode.Type.IN, null);
    }

    @Test
    public void typeConstructorSuccess() {
        final MultiComparisonNode filterNode = new MultiComparisonNode(
                MultiComparisonNode.Type.IN, "propertyName");
        assertThat(filterNode.getComparisonType()).isEqualTo(
                MultiComparisonNode.Type.IN);
        assertThat(filterNode.getComparisonProperty()).isEqualTo("propertyName");
        assertThat(filterNode.getComparisonValue()).isEmpty();

        filterNode.addValue("test");
        assertThat(filterNode.getComparisonValue().get(0)).isEqualTo("test");
    }

    @Test
    public void addNullValues() {
        final MultiComparisonNode filterNode = new MultiComparisonNode(
                MultiComparisonNode.Type.IN, "propertyName");

        filterNode.addValue("test1");
        filterNode.addValue(null);
        filterNode.addValue("test2");
        filterNode.addValue(null);
        filterNode.addValue(null);
        filterNode.addValue("test3");
        assertThat(filterNode.getComparisonValue().get(0)).isEqualTo("test1");
        assertThat(filterNode.getComparisonValue().get(1)).isNull();
        assertThat(filterNode.getComparisonValue().get(2)).isEqualTo("test2");
        assertThat(filterNode.getComparisonValue().get(3)).isNull();
        assertThat(filterNode.getComparisonValue().get(4)).isNull();
        assertThat(filterNode.getComparisonValue().get(5)).isEqualTo("test3");
    }

    @Test
    public void visitorGetsVisited() {
        final PredicateVisitor visitorMock = mock(PredicateVisitor.class);
        final MultiComparisonNode multiComparisonNode = new MultiComparisonNode(
                MultiComparisonNode.Type.IN, "propertyName");
        multiComparisonNode.accept(visitorMock);
        verify(visitorMock).visit(multiComparisonNode);
    }

}
