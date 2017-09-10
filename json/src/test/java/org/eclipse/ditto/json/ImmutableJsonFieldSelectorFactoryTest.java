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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.JsonFactory.newPointer;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableJsonFieldSelectorFactory}.
 */
public final class ImmutableJsonFieldSelectorFactoryTest {

    private static final JsonParseOptions JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private static final JsonParseOptions JSON_PARSE_OPTIONS_WITH_URL_DECODING =
            JsonFactory.newParseOptionsBuilder().withUrlDecoding().build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonFieldSelectorFactory.class, areImmutable());
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullString() {
        ImmutableJsonFieldSelectorFactory.newInstance(null, JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullOptions() {
        ImmutableJsonFieldSelectorFactory.newInstance("foo", null);
    }


    @Test(expected = JsonFieldSelectorInvalidException.class)
    public void tryToCreateInstanceWithEmptyString() {
        ImmutableJsonFieldSelectorFactory.newInstance("", JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
    }


    @Test(expected = JsonFieldSelectorInvalidException.class)
    public void tryToCreateInstanceWithStringContainingUnevenCountOfOpeningAndClosingParentheses() {
        final String jsonFieldSelectorString = "thingId,attributes(someAttr/subsel,owner";
        ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString, JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
    }


    @Test
    public void newJsonFieldSelectorReturnsExpected() {
        final String jsonFieldSelectorString = "thingId,attributes(someAttr/subel,foo),owner";
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
        final JsonFieldSelector jsonFieldSelector = underTest.newJsonFieldSelector();
        final byte expectedSize = 4;

        Assertions.assertThat(jsonFieldSelector).hasSize(expectedSize);
    }


    @Test
    public void newJsonFieldSelectorFromSimpleKeyString() {
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance("thingId", JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
        final JsonFieldSelector jsonFieldSelector = underTest.newJsonFieldSelector();

        Assertions.assertThat(jsonFieldSelector).hasSize(1);
    }


    @Test
    public void newJsonFieldSelectorFromStringWithSubFields() {
        final String jsonFieldSelectorString = "one,two(subfield1,sub2),three(foo,bar/baz),four";
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
        final JsonFieldSelector jsonFieldSelector = underTest.newJsonFieldSelector();
        final byte expectedSize = 6;

        final JsonPointer onePointer = newPointer("one");
        final JsonPointer twoSubfield1Pointer = JsonFactory.newPointer(JsonFactory.newKey("two"), JsonFactory.newKey("subfield1"));
        final JsonPointer twoSub2Pointer = JsonFactory.newPointer(JsonFactory.newKey("two"), JsonFactory.newKey("sub2"));
        final JsonPointer threeFooPointer = JsonFactory.newPointer(JsonFactory.newKey("three"), JsonFactory.newKey("foo"));
        final JsonPointer threeBarPointer = JsonFactory.newPointer(JsonFactory.newKey("three"), JsonFactory.newKey("bar"), JsonFactory
                .newKey("baz"));
        final JsonPointer fourPointer = JsonFactory.newPointer(JsonFactory.newKey("four"));

        final Set<JsonPointer> actualPointers = jsonFieldSelector.getPointers();

        assertThat(actualPointers).hasSize(expectedSize);
        assertThat(actualPointers).contains(onePointer, twoSubfield1Pointer, twoSub2Pointer, threeFooPointer,
                threeBarPointer, fourPointer);
    }


    @Test
    public void newJsonFieldSelectorFromStringWithNestedSubFields() {
        final String jsonFieldSelectorString = "one,two(subfield1,sub2(foo,bar/baz)),four,five/key";
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
        final JsonFieldSelector jsonFieldSelector = underTest.newJsonFieldSelector();
        final byte expectedSize = 6;

        Assertions.assertThat(jsonFieldSelector).hasSize(expectedSize);
    }


    @Test(expected = JsonFieldSelectorInvalidException.class)
    public void newJsonFieldSelectorFromInvalidEncodedSubFields() {
        final String jsonFieldSelectorString = "feature-1%2properties";
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITH_URL_DECODING);
        underTest.newJsonFieldSelector();
    }


    @Test(expected = JsonFieldSelectorInvalidException.class)
    public void newJsonFieldSelectorFromInvalidParenthesis() throws UnsupportedEncodingException {
        final String jsonFieldSelectorString = "abc(def(foo)";
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITHOUT_URL_DECODING);
        underTest.newJsonFieldSelector();
    }


    @Test(expected = JsonFieldSelectorInvalidException.class)
    public void newJsonFieldSelectorFromInvalidParenthesisEncoded() throws UnsupportedEncodingException {
        final String jsonFieldSelectorString = URLEncoder.encode("abc(def(foo)", "UTF-8");
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITH_URL_DECODING);
        underTest.newJsonFieldSelector();
    }


    @Test
    public void newJsonFieldSelectorFromInvalidEmptyParenthesis() throws UnsupportedEncodingException {
        final String jsonFieldSelectorString = URLEncoder.encode("abc()", "UTF-8");
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITH_URL_DECODING);
        underTest.newJsonFieldSelector();
    }


    @Test
    public void newJsonFieldSelectorFromInvalidParenthesisOrder() throws UnsupportedEncodingException {
        final String jsonFieldSelectorString = URLEncoder.encode("abc)(", "UTF-8");
        final ImmutableJsonFieldSelectorFactory underTest =
                ImmutableJsonFieldSelectorFactory.newInstance(jsonFieldSelectorString,
                        JSON_PARSE_OPTIONS_WITH_URL_DECODING);
        underTest.newJsonFieldSelector();
    }

}
