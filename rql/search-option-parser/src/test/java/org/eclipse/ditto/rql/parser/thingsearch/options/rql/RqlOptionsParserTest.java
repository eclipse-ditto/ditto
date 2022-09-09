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
package org.eclipse.ditto.rql.parser.thingsearch.options.rql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.parser.thingsearch.OptionParser;
import org.eclipse.ditto.rql.parser.thingsearch.RqlOptionParser;
import org.eclipse.ditto.thingsearch.model.CursorOption;
import org.eclipse.ditto.thingsearch.model.LimitOption;
import org.eclipse.ditto.thingsearch.model.Option;
import org.eclipse.ditto.thingsearch.model.SizeOption;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.model.SortOptionEntry;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.rql.parser.thingsearch.RqlOptionParser}.
 */
public final class RqlOptionsParserTest {

    private final OptionParser parser = new RqlOptionParser();

    @Test
    public void parseSortWithOnePropertyAscending() throws ParserException {
        final List<Option> options = parser.parse("sort(+username)");
        assertThat(options).hasSize(1);
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
        assertThat(options).hasSize(1);
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

        assertThat(options).hasSize(1);

        final SortOption sortOption = (SortOption) options.get(0);
        assertThat(sortOption.getEntries()).hasSize(expectedCount);

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
        final List<Option> options = parser.parse("limit(0,1),sort(-attributes/username),cursor(ABC),size(463)");
        assertThat(options).hasSize(4);

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

        final CursorOption cursorOption = (CursorOption) options.get(2);
        assertThat(cursorOption.getCursor()).isEqualTo("ABC");

        final SizeOption sizeOption = (SizeOption) options.get(3);
        assertThat(sizeOption.getSize()).isEqualTo(463);
    }

    @Test
    public void parseAndUnparseAreInverseOfEachOther() throws ParserException {
        final String input = "limit(0,1),sort(-attributes/username)";
        final List<Option> parsed = parser.parse(input);
        final String unparsed = RqlOptionParser.unparse(parsed);
        final List<Option> reParsed = parser.parse(unparsed);

        assertThat(reParsed).isEqualTo(parsed);
    }

    @Test(expected = ParserException.class)
    public void invalidLimitArgumentsExceedsLong() throws ParserException {
        parser.parse("limit(100000000000000000000,10)");
    }

    @Test(expected = ParserException.class)
    public void cursorWithoutContentOrClosingParenthesis() throws ParserException {
        parser.parse("cursor(");
    }

    @Test(expected = ParserException.class)
    public void cursorWithoutClosingParenthesis() throws ParserException {
        parser.parse("cursor(0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz");
    }

}
