/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.rql.query.things;

import java.util.Set;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.rql.parser.internal.RqlPredicateParser;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link FieldNamesPredicateVisitor}
 */
public final class FieldNamesPredicateVisitorTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void verifyCorrectFiltering() {
        softly.assertThat(extractFieldsFromRql("and(eq(attributes/foo,1),exists(_created))"))
                .containsExactlyInAnyOrder("attributes/foo", "_created");
        softly.assertThat(extractFieldsFromRql("or(exists(attributes/foo),exists(attributes/foo2))"))
                .containsExactlyInAnyOrder("attributes/foo", "attributes/foo2");
        softly.assertThat(extractFieldsFromRql("eq(features/*/definition,\"t:t:1\")"))
                .containsExactlyInAnyOrder("features/*/definition");
        softly.assertThat(extractFieldsFromRql("eq(/attributes/complex,\"!#$%&'(features)*+,/:;=?@[\\\\]{|}\\\" äaZ0\")"))
                .containsExactlyInAnyOrder("/attributes/complex");
        softly.assertThat(extractFieldsFromRql("or(eq(_metadata/attributes/manufacturer,1),exists(_metadata))"))
                .containsExactlyInAnyOrder("_metadata/attributes/manufacturer", "_metadata");
    }

    private static Set<String> extractFieldsFromRql(final String filter) {
        final FieldNamesPredicateVisitor fieldNameVisitor = FieldNamesPredicateVisitor.getNewInstance();
        fieldNameVisitor.visit(RqlPredicateParser.parse(filter));
        return fieldNameVisitor.getFieldNames();
    }

}
