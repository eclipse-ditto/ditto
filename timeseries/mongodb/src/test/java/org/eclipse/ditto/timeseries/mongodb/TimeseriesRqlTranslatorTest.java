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
package org.eclipse.ditto.timeseries.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.bson.BsonDocument;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.junit.Test;

import com.mongodb.MongoClientSettings;

/**
 * Unit tests for {@link TimeseriesRqlTranslator}: RQL in, MongoDB filter over {@code meta.tags.*} out.
 */
public final class TimeseriesRqlTranslatorTest {

    private static final CodecRegistry CODECS = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry());

    private static String render(final Bson filter) {
        return filter.toBsonDocument(BsonDocument.class, CODECS).toJson();
    }

    @Test
    public void eqTargetsTheTagSubDocument() {
        assertThat(render(TimeseriesRqlTranslator.translate("eq(attributes/building,'A')")))
                .isEqualTo("{\"meta.tags.attributes/building\": \"A\"}");
    }

    @Test
    public void aLeadingSlashAddressesTheSameTag() {
        assertThat(render(TimeseriesRqlTranslator.translate("eq(/attributes/building,'A')")))
                .isEqualTo(render(TimeseriesRqlTranslator.translate("eq(attributes/building,'A')")));
    }

    @Test
    public void comparisonOperatorsMapToTheirMongoEquivalents() {
        assertThat(render(TimeseriesRqlTranslator.translate("ne(attributes/floor,2)"))).contains("$ne");
        assertThat(render(TimeseriesRqlTranslator.translate("gt(attributes/floor,2)"))).contains("$gt");
        assertThat(render(TimeseriesRqlTranslator.translate("ge(attributes/floor,2)"))).contains("$gte");
        assertThat(render(TimeseriesRqlTranslator.translate("lt(attributes/floor,2)"))).contains("$lt");
        assertThat(render(TimeseriesRqlTranslator.translate("le(attributes/floor,2)"))).contains("$lte");
    }

    @Test
    public void inBecomesAnInFilter() {
        final String rendered = render(TimeseriesRqlTranslator.translate(
                "in(attributes/floor,'1','2','3')"));
        assertThat(rendered).contains("$in").contains("\"1\"").contains("\"3\"");
    }

    @Test
    public void logicalOperatorsNest() {
        assertThat(render(TimeseriesRqlTranslator.translate(
                "and(eq(attributes/building,'A'),ge(attributes/floor,2))"))).contains("$and");
        assertThat(render(TimeseriesRqlTranslator.translate(
                "or(eq(attributes/building,'A'),eq(attributes/building,'B'))"))).contains("$or");
        // not() has no direct Mongo operator; $nor over a single clause is the equivalent.
        assertThat(render(TimeseriesRqlTranslator.translate("not(eq(attributes/building,'A'))")))
                .contains("$nor");
    }

    /**
     * {@code like} wildcards become a regex, but literal segments are quoted — a value containing regex
     * metacharacters must not be able to smuggle a pattern past the filter.
     */
    @Test
    public void likeQuotesRegexMetacharactersInLiteralSegments() {
        final String wildcard = render(TimeseriesRqlTranslator.translate(
                "like(attributes/building,'Building-*')"));
        assertThat(wildcard).contains("$regularExpression");
        // Literal part quoted with \Q...\E, wildcard translated to .*
        assertThat(wildcard).contains("Q" + "Building-").contains(".*");
        // The '.' in a value must be quoted, not left meaning "any character".
        final String dotted = render(TimeseriesRqlTranslator.translate(
                "like(attributes/building,'a.c')"));
        assertThat(dotted).contains("Qa.c");
    }

    @Test
    public void existsChecksForThePresenceOfTheTag() {
        assertThat(render(TimeseriesRqlTranslator.translate("exists(attributes/building)")))
                .contains("$exists");
    }

    /**
     * A tag key is a Thing path and so contains no {@code .} or {@code $}. Rejecting those keeps the
     * filter from being read as a nested field reference or a Mongo operator.
     */
    @Test
    public void aFieldReferenceContainingDotOrDollarIsRejected() {
        for (final String rql : new String[] {"eq(a.b,'x')", "eq($where,'x')"}) {
            assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                    .isThrownBy(() -> TimeseriesRqlTranslator.translate(rql));
        }
    }

    @Test
    public void unparseableRqlIsRejectedWithAnActionableMessage() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> TimeseriesRqlTranslator.translate("this is not rql"))
                .withMessageContaining("not a valid RQL predicate");
    }

    @Test
    public void referencedFieldsAreReportedForAuthorization() {
        assertThat(TimeseriesRqlTranslator.referencedFields(
                "and(eq(attributes/building,'A'),ge(attributes/floor,2))"))
                .containsExactly("attributes/building", "attributes/floor");
    }

    @Test
    public void referencedFieldsAreDeduplicated() {
        assertThat(TimeseriesRqlTranslator.referencedFields(
                "or(eq(attributes/building,'A'),eq(attributes/building,'B'))"))
                .containsExactly("attributes/building");
    }
}
