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
package org.eclipse.ditto.model.thingsearchparser.options.rql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.thingsearch.LimitOption;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
import org.eclipse.ditto.model.thingsearchparser.ParserException;
import org.junit.Test;

/**
 * Unit test for {@link RqlOptionParser}.
 */
public final class RqlOptionsParserTest {

    private final OptionParser parser = new RqlOptionParser();

    @Test
    public void parseSortWithOnePropertyAscending() throws ParserException {
        final List<Option> options = parser.parse("sort(+username)");
        assertThat(options.size()).isEqualTo(1);
        final SortOption sortOption = (SortOption) options.get(0);
        assertThat(sortOption.getEntries().stream()
                .map(SortOptionEntry::getPropertyPath)
                .map(JsonPointer::toString)
                .anyMatch("/username"::equals)
        ).isTrue();
        assertThat(sortOption.getEntries().stream()
                .filter(soe -> JsonPointer.of("/username").equals(soe.getPropertyPath()))
                .map(SortOptionEntry::getOrder)
                .anyMatch(SortOptionEntry.SortOrder.ASC::equals)
        ).isTrue();
    }

    @Test
    public void parseSortWithOnePropertyDescending() throws ParserException {
        final List<Option> options = parser.parse("sort(-attributes/username)");
        assertThat(options.size()).isEqualTo(1);
        final SortOption sortOption = (SortOption) options.get(0);
        assertThat(sortOption.getEntries().stream()
                .map(SortOptionEntry::getPropertyPath)
                .map(JsonPointer::toString)
                .anyMatch("/attributes/username"::equals)
        ).isTrue();
        assertThat(sortOption.getEntries().stream()
                .filter(soe -> JsonPointer.of("/attributes/username").equals(soe.getPropertyPath()))
                .map(SortOptionEntry::getOrder)
                .anyMatch(SortOptionEntry.SortOrder.DESC::equals)
        ).isTrue();
    }

    @Test
    public void parseSortWithSeveralProperties() throws ParserException {
        final int expectedCount = 20;
        final StringBuilder sb = new StringBuilder();
        sb.append("sort(");
        for (int i = 0; i < expectedCount; i++) {
            sb.append(i % 2 == 0 ? "+" : "-");
            sb.append("field").append(i);
            sb.append(",");
        }
        sb.replace(sb.length() - 1, sb.length(), ")");

        final List<Option> options = parser.parse(sb.toString());

        assertThat(options.size()).isEqualTo(1);

        final SortOption sortOption = (SortOption) options.get(0);
        assertThat(sortOption.getEntries().size()).isEqualTo(expectedCount);

        for (int i = 0; i < expectedCount; i++) {
            final int idx = i;
            assertThat(sortOption.getEntries().stream()
                    .filter(soe -> JsonPointer.of("field" + idx).equals(soe.getPropertyPath()))
                    .map(SortOptionEntry::getOrder)
                    .anyMatch(so -> idx % 2 == 0 ? SortOptionEntry.SortOrder.ASC.equals(so) :
                            SortOptionEntry.SortOrder.DESC.equals(so))
            ).isTrue();
        }
    }

    @Test(expected = ParserException.class)
    public void invalidSortMissingOrder() throws ParserException {
        parser.parse("sort(username)");
    }

    @Test(expected = ParserException.class)
    public void invalidSortMissingOrderAtSecondProperty() throws ParserException {
        parser.parse("sort(-username,owner)");
    }

    @Test
    public void parseLimitSuccess() throws ParserException {
        final List<Option> options = parser.parse("limit(0,1)");
        assertThat(options.size()).isEqualTo(1);
        final LimitOption limitOption = (LimitOption) options.get(0);
        assertThat(limitOption.getCount()).isEqualTo(1);
        assertThat(limitOption.getOffset()).isEqualTo(0);
    }

    @Test(expected = ParserException.class)
    public void invalidLimitMissingNumber() throws ParserException {
        parser.parse("limit(0)");
    }

    @Test(expected = ParserException.class)
    public void invalidLimitTooManyArguments() throws ParserException {
        parser.parse("limit(0,1,2)");
    }

    @Test
    public void parseOptionCombinations() throws ParserException {
        final List<Option> options = parser.parse("limit(0,1),sort(-attributes/username)");
        assertThat(options.size()).isEqualTo(2);

        final LimitOption limitOption = (LimitOption) options.get(0);
        assertThat(limitOption.getCount()).isEqualTo(1);
        assertThat(limitOption.getOffset()).isEqualTo(0);

        final SortOption sortOption = (SortOption) options.get(1);
        assertThat(sortOption.getEntries().stream()
                .map(SortOptionEntry::getPropertyPath)
                .map(JsonPointer::toString)
                .anyMatch("/attributes/username"::equals)
        ).isTrue();
        assertThat(sortOption.getEntries().stream()
                .filter(soe -> JsonPointer.of("/attributes/username").equals(soe.getPropertyPath()))
                .map(SortOptionEntry::getOrder)
                .anyMatch(SortOptionEntry.SortOrder.DESC::equals)
        ).isTrue();
    }

    @Test(expected = ParserException.class)
    public void invalidLimitArgumentsExceedsLong() throws ParserException {
        parser.parse("limit(100000000000000000000,10)");
    }
}
