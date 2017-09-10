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
package org.eclipse.ditto.json;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit test for {@link ImmutableJsonArrayBuilder}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableJsonArrayBuilderTest {


    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherBooleanNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.add(true, null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherIntegerNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.add(1, (Integer) null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherLongNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.add(1, (Long) null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherDoubleNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.add(1.1, null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddStringNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.add((String) null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddJsonValueNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.add((JsonValue) null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddAllJsonValueNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.addAll(null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToRemoveWithNull() {
        final JsonArrayBuilder underTest = ImmutableJsonArrayBuilder.newInstance();
        underTest.remove(null);
    }

}
