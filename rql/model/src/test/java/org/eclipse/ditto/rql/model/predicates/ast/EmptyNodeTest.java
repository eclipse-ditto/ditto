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
package org.eclipse.ditto.rql.model.predicates.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link EmptyNode}.
 */
public class EmptyNodeTest {

    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(EmptyNode.class).usingGetClass().verify();
    }

    @Test(expected = NullPointerException.class)
    public void typeConstructorWithNull() {
        new EmptyNode(null);
    }

    @Test
    public void constructorSuccess() {
        final EmptyNode emptyNode = new EmptyNode("propertyName");
        assertThat(emptyNode.getProperty()).isEqualTo("propertyName");
    }

    @Test
    public void visitorGetsVisited() {
        final PredicateVisitor visitorMock = mock(PredicateVisitor.class);
        final EmptyNode emptyNode = new EmptyNode("propertyName");
        emptyNode.accept(visitorMock);
        verify(visitorMock).visit(emptyNode);
    }

}
