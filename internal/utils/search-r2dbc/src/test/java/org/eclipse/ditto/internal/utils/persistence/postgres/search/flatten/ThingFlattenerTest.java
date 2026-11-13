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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.model.PoliciesResourceType.THING;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexDocumentFactory;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.junit.Test;

/**
 * TDD spec for {@link ThingFlattener}: the shape cases of its flattening rules, the RFC-6901 escaping cases and a
 * {@link SearchIndexDocument}-level integration case on real {@code JsonObject} payloads.
 */
public class ThingFlattenerTest {

    private static final String THING_ID = "flattener.test:thing-1";

    // --- rule 3: dual-wpath on every feature-subtree row (scalar leaves) ------------------------------------------

    @Test
    public void featureSubtreeScalarLeafEmitsTwoRowsWithFeatureId() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "features": {
                    "env": {
                      "properties": {
                        "temperature": 21.5
                      }
                    }
                  }
                }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final String path = "/features/env/properties/temperature";
        final List<FlatRow> matching = rowsAtPath(rows, path);
        assertThat(matching).hasSize(2);
        assertThat(matching).allSatisfy(row -> {
            assertThat(row.path()).isEqualTo(path);
            assertThat(row.fId()).isEqualTo("env");
            assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_NUMBER);
            assertThat(row.valNum()).isEqualByComparingTo("21.5");
            assertThat(row.valBool()).isNull();
            assertThat(row.valText()).isNull();
        });
        assertThat(matching.stream().map(FlatRow::wpath))
                .containsExactlyInAnyOrder(path, "/features/*/properties/temperature");
    }

    // --- invariant the sort lateral relies on: wpath differs from path only on the /features/* twin rows ----------

    @Test
    public void everyRowOutsideTheFeaturesWildcardTwinsHasWpathEqualToPath() {
        // The sort lateral binds "path = wpath = <sort wpath>" to use the full primary-key prefix. That is
        // result-preserving only because a row whose wpath is not the /features/* twin always carries wpath == path,
        // whatever the payload shape.
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "_modified": "2026-10-01T00:00:00Z",
                  "policyId": "ns:policy",
                  "attributes": {
                    "a/b~c": { "deep": [1, "x", null, true, {"n": 2}, [3, 4], []] },
                    "empty": {}, "nothing": null
                  },
                  "features": {
                    "env": {
                      "definition": ["ns:def:1.0.0"],
                      "properties": { "temperature": [21.5, 19], "nested": { "k": "v" } },
                      "desiredProperties": { "temperature": 20 }
                    },
                    "x/y~z": { "properties": { "temperature": 18 } }
                  }
                }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        assertThat(rows).filteredOn(row -> !row.wpath().startsWith("/features/*"))
                .hasSizeGreaterThan(20)
                .allSatisfy(row -> assertThat(row.wpath()).isEqualTo(row.path()));
        assertThat(rows).filteredOn(row -> row.wpath().startsWith("/features/*"))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.path()).isNotEqualTo(row.wpath()));
    }

    // --- rule 3 + rule 6: dual-wpath applies to object/empty-container exists-rows too ------------------------------

    @Test
    public void featureSubtreeObjectAndEmptyContainerRowsAlsoEmitTwice() {
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("env", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("emptyObj", JsonFactory.newObject())
                                        .set("emptyArr", JsonFactory.newArray())
                                        .build())
                                .build())
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final List<FlatRow> propertiesRows = rowsAtPath(rows, "/features/env/properties");
        assertThat(propertiesRows).hasSize(2);
        assertThat(propertiesRows).allSatisfy(row -> {
            assertThat(row.fId()).isEqualTo("env");
            assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_OBJECT);
        });
        assertThat(propertiesRows.stream().map(FlatRow::wpath))
                .containsExactlyInAnyOrder("/features/env/properties", "/features/*/properties");

        final List<FlatRow> emptyObjRows = rowsAtPath(rows, "/features/env/properties/emptyObj");
        assertThat(emptyObjRows).hasSize(2);
        assertThat(emptyObjRows).allSatisfy(row -> {
            assertThat(row.fId()).isEqualTo("env");
            assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_OBJECT);
        });
        assertThat(emptyObjRows.stream().map(FlatRow::wpath))
                .containsExactlyInAnyOrder("/features/env/properties/emptyObj", "/features/*/properties/emptyObj");

        final List<FlatRow> emptyArrRows = rowsAtPath(rows, "/features/env/properties/emptyArr");
        assertThat(emptyArrRows).hasSize(2);
        assertThat(emptyArrRows).allSatisfy(row -> {
            assertThat(row.fId()).isEqualTo("env");
            assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_ARRAY);
        });
        assertThat(emptyArrRows.stream().map(FlatRow::wpath))
                .containsExactlyInAnyOrder("/features/env/properties/emptyArr", "/features/*/properties/emptyArr");
    }

    // --- rule 3 negative: non-feature rows are single, fId null -----------------------------------------------------

    @Test
    public void nonFeatureRowsAreSingleWithNullFeatureId() {
        final JsonObject thing = JsonFactory.newObject("""
                { "attributes": { "color": "red" } }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final List<FlatRow> attributesRows = rowsAtPath(rows, "/attributes");
        assertThat(attributesRows).hasSize(1);
        assertThat(attributesRows.get(0).fId()).isNull();
        assertThat(attributesRows.get(0).wpath()).isEqualTo("/attributes");
        assertThat(attributesRows.get(0).typeRank()).isEqualTo(ThingFlattener.TYPE_OBJECT);

        final List<FlatRow> colorRows = rowsAtPath(rows, "/attributes/color");
        assertThat(colorRows).hasSize(1);
        final FlatRow colorRow = colorRows.get(0);
        assertThat(colorRow.fId()).isNull();
        assertThat(colorRow.path()).isEqualTo(colorRow.wpath());
        assertThat(colorRow.typeRank()).isEqualTo(ThingFlattener.TYPE_STRING);
        assertThat(colorRow.valText()).isEqualTo("red");
    }

    // --- rule 4 + rule 5: ord enumerates repeat occurrences, including through nested object explosion --------------

    @Test
    public void ordEnumeratesRepeatOccurrencesAtSamePathIncludingThroughNestedObjectExplosion() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "attributes": {
                    "tags": ["red", "green", "blue"],
                    "readings": [ { "value": 10 }, { "value": 20 } ]
                  }
                }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final List<FlatRow> tagRows = rowsAtPath(rows, "/attributes/tags");
        assertThat(tagRows).extracting(FlatRow::ord).containsExactlyInAnyOrder(0, 1, 2);
        assertThat(tagRows).extracting(FlatRow::valText).containsExactlyInAnyOrder("red", "green", "blue");

        // the "readings" array itself contributes no container row (non-empty, non-nested array)
        final List<FlatRow> readingsContainerRows = rows.stream()
                .filter(r -> r.path().equals("/attributes/readings"))
                .filter(r -> r.typeRank() == ThingFlattener.TYPE_ARRAY)
                .toList();
        assertThat(readingsContainerRows).isEmpty();

        final List<FlatRow> readingsObjectRows = rowsAtPath(rows, "/attributes/readings");
        assertThat(readingsObjectRows).extracting(FlatRow::ord).containsExactlyInAnyOrder(0, 1);
        assertThat(readingsObjectRows).allSatisfy(
                row -> assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_OBJECT));

        final List<FlatRow> valueRows = rowsAtPath(rows, "/attributes/readings/value");
        assertThat(valueRows).extracting(FlatRow::ord).containsExactlyInAnyOrder(0, 1);
        assertThat(valueRows.stream().map(r -> r.valNum().intValue()).toList())
                .containsExactlyInAnyOrder(10, 20);
    }

    @Test
    public void ordResetsBetweenSeparateFlattenCalls() {
        final JsonObject thing = JsonFactory.newObject("""
                { "attributes": { "tags": ["red", "green", "blue"] } }
                """);

        final List<FlatRow> firstCall = ThingFlattener.flatten(THING_ID, thing);
        final List<FlatRow> secondCall = ThingFlattener.flatten(THING_ID, thing);

        assertThat(rowsAtPath(firstCall, "/attributes/tags")).extracting(FlatRow::ord)
                .containsExactlyInAnyOrder(0, 1, 2);
        assertThat(rowsAtPath(secondCall, "/attributes/tags")).extracting(FlatRow::ord)
                .containsExactlyInAnyOrder(0, 1, 2);
    }

    // --- rule 4: (ord,value) PAIRING and same-ord-across-dual-rows, pinned explicitly --------------------------------

    @Test
    public void dualRowsShareTheSameOrdAndPairCorrectlyWithTheirValueAcrossBothWpathVariants() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "features": {
                    "env": { "properties": { "readings": ["ten", "twenty", "thirty"] } }
                  }
                }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);
        final List<FlatRow> readingRows = rowsAtPath(rows, "/features/env/properties/readings");
        assertThat(readingRows).hasSize(6); // 3 elements * 2 (dual-wpath)

        final Map<String, String> pathVariantByOrd = readingRows.stream()
                .filter(r -> r.wpath().equals("/features/env/properties/readings"))
                .collect(Collectors.toMap(r -> String.valueOf(r.ord()), FlatRow::valText));
        final Map<String, String> starVariantByOrd = readingRows.stream()
                .filter(r -> r.wpath().equals("/features/*/properties/readings"))
                .collect(Collectors.toMap(r -> String.valueOf(r.ord()), FlatRow::valText));

        assertThat(pathVariantByOrd).containsExactlyInAnyOrderEntriesOf(Map.of("0", "ten", "1", "twenty", "2", "thirty"));
        // the star-variant map must be the exact SAME (ord -> value) pairing as the path-variant map, not merely an
        // overlapping set of values/ords: this pins that a dual-row emission shares one `ord`, not two independently
        // enumerated ones.
        assertThat(starVariantByOrd).isEqualTo(pathVariantByOrd);
    }

    @Test
    public void ordCounterIsScopedPerPathNotAccidentallySharedViaTheStarWildcardedWpath() {
        // Two DIFFERENT features each with one scalar leaf at the same relative sub-path: their star wpaths are
        // IDENTICAL strings ("/features/*/properties/temperature"), but their `path`s differ (they embed the
        // feature ID). If `ord` were (incorrectly) enumerated per-wpath instead of per-path, the second feature's
        // leaf would wrongly inherit a continued counter from the first. Both must independently be ord 0.
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "features": {
                    "env": { "properties": { "temperature": 1 } },
                    "other": { "properties": { "temperature": 2 } }
                  }
                }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final FlatRow envRow = onlyRowAt(rows, "/features/env/properties/temperature", "env");
        final FlatRow otherRow = onlyRowAt(rows, "/features/other/properties/temperature", "other");
        assertThat(envRow.ord()).isZero();
        assertThat(otherRow.ord()).isZero();
    }

    private static FlatRow onlyRowAt(final List<FlatRow> rows, final String path, final String fId) {
        final List<FlatRow> matching = rows.stream()
                .filter(r -> r.path().equals(path) && r.wpath().equals(path) && fId.equals(r.fId()))
                .toList();
        assertThat(matching).as("exact-wpath row at %s for feature %s", path, fId).hasSize(1);
        return matching.get(0);
    }

    // --- rule 5: array-of-array stub, the ONLY non-explosion case -----------------------------------------------

    @Test
    public void directArrayOfArrayIsNotExplodedAndYieldsValuelessArrayStubRows() {
        final JsonObject thing = JsonFactory.newObject("""
                { "attributes": { "matrix": [[1,2],[3,4],[5,6]] } }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final List<FlatRow> matrixRows = rowsAtPath(rows, "/attributes/matrix");
        assertThat(matrixRows).hasSize(3);
        assertThat(matrixRows).allSatisfy(row -> {
            assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_ARRAY);
            assertThat(row.valBool()).isNull();
            assertThat(row.valNum()).isNull();
            assertThat(row.valText()).isNull();
        });
        assertThat(matrixRows).extracting(FlatRow::ord).containsExactlyInAnyOrder(0, 1, 2);

        assertThat(rows).noneMatch(r -> r.valNum() != null
                && List.of(1, 2, 3, 4, 5, 6).contains(r.valNum().intValue()));
    }

    @Test
    public void nestedArrayOfArrayInsideDeeperStructureIsAlsoStubbedNotExploded() {
        // rule 5's stub applies wherever an array element is itself an array, at ANY nesting depth reached via
        // recursive explosion through objects/arrays -- not just when the array-of-arrays is a direct thing child.
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "attributes": {
                    "readings": [ { "cube": [[1,2],[3,4]] } ]
                  }
                }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final List<FlatRow> cubeRows = rowsAtPath(rows, "/attributes/readings/cube");
        assertThat(cubeRows).hasSize(2);
        assertThat(cubeRows).allSatisfy(row -> assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_ARRAY));
        assertThat(rows).noneMatch(r -> r.valNum() != null);
    }

    // --- rule 6: type_rank mapping + exclusive scalar value slots -----------------------------------------------

    @Test
    public void typeRankMappingAndExclusiveScalarValueSlots() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "attributes": {
                    "aNull": null,
                    "aNumber": 3.5,
                    "aString": "hello",
                    "aBoolean": true,
                    "anObject": { "k": "v" },
                    "anArray": ["x", "y"]
                  }
                }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final FlatRow nullRow = onlyRowAtPath(rows, "/attributes/aNull");
        assertThat(nullRow.typeRank()).isEqualTo(ThingFlattener.TYPE_NULL);
        assertThat(nullRow.valBool()).isNull();
        assertThat(nullRow.valNum()).isNull();
        assertThat(nullRow.valText()).isNull();

        final FlatRow numberRow = onlyRowAtPath(rows, "/attributes/aNumber");
        assertThat(numberRow.typeRank()).isEqualTo(ThingFlattener.TYPE_NUMBER);
        assertThat(numberRow.valNum()).isEqualByComparingTo("3.5");
        assertThat(numberRow.valBool()).isNull();
        assertThat(numberRow.valText()).isNull();

        final FlatRow stringRow = onlyRowAtPath(rows, "/attributes/aString");
        assertThat(stringRow.typeRank()).isEqualTo(ThingFlattener.TYPE_STRING);
        assertThat(stringRow.valText()).isEqualTo("hello");
        assertThat(stringRow.valBool()).isNull();
        assertThat(stringRow.valNum()).isNull();

        final FlatRow booleanRow = onlyRowAtPath(rows, "/attributes/aBoolean");
        assertThat(booleanRow.typeRank()).isEqualTo(ThingFlattener.TYPE_BOOLEAN);
        assertThat(booleanRow.valBool()).isTrue();
        assertThat(booleanRow.valNum()).isNull();
        assertThat(booleanRow.valText()).isNull();

        final FlatRow objectRow = onlyRowAtPath(rows, "/attributes/anObject");
        assertThat(objectRow.typeRank()).isEqualTo(ThingFlattener.TYPE_OBJECT);
        assertThat(objectRow.valBool()).isNull();
        assertThat(objectRow.valNum()).isNull();
        assertThat(objectRow.valText()).isNull();

        final List<FlatRow> arrayRows = rowsAtPath(rows, "/attributes/anArray");
        assertThat(arrayRows).hasSize(2);
        assertThat(arrayRows).allSatisfy(row -> assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_STRING));
    }

    @Test
    public void emptyObjectAndEmptyArrayEachProduceExactlyOneRow() {
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("emptyObj", JsonFactory.newObject())
                        .set("emptyArr", JsonFactory.newArray())
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final FlatRow emptyObjRow = onlyRowAtPath(rows, "/attributes/emptyObj");
        assertThat(emptyObjRow.typeRank()).isEqualTo(ThingFlattener.TYPE_OBJECT);
        assertThat(emptyObjRow.fId()).isNull();

        final FlatRow emptyArrRow = onlyRowAtPath(rows, "/attributes/emptyArr");
        assertThat(emptyArrRow.typeRank()).isEqualTo(ThingFlattener.TYPE_ARRAY);
        assertThat(emptyArrRow.fId()).isNull();

        assertThat(rows.stream().filter(r -> r.path().startsWith("/attributes/emptyObj/"))).isEmpty();
        assertThat(rows.stream().filter(r -> r.path().startsWith("/attributes/emptyArr/"))).isEmpty();
    }

    // --- rule 8: numbers preserve int/long/double exactly, no precision loss ------------------------------------

    @Test
    public void numbersPreserveIntLongDoubleExactlyWithoutPrecisionLoss() {
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("anInt", 42)
                        .set("aLongBeyondIntRange", 5_000_000_000L)
                        // 2^53 + 1: NOT exactly representable as a double: a naive Number->doubleValue()->BigDecimal
                        // conversion would silently round this to 9007199254740992 (lost the +1).
                        .set("aLongPastDoublePrecision", 9_007_199_254_740_993L)
                        .set("aFraction", 3.14159)
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        assertThat(onlyRowAtPath(rows, "/attributes/anInt").valNum()).isEqualByComparingTo("42");
        assertThat(onlyRowAtPath(rows, "/attributes/aLongBeyondIntRange").valNum())
                .isEqualByComparingTo("5000000000");
        assertThat(onlyRowAtPath(rows, "/attributes/aLongPastDoublePrecision").valNum())
                .isEqualByComparingTo(new BigDecimal("9007199254740993"));
        assertThat(onlyRowAtPath(rows, "/attributes/aFraction").valNum()).isEqualByComparingTo("3.14159");
    }

    // --- rule 1 + rule 7: flatten from thing() ONLY; never leaks auth/bookkeeping fields -------------------------

    @Test
    public void onlyThePayloadIsFlattenedNoExtraBookkeepingRows() {
        final JsonObject thing = JsonFactory.newObject("""
                { "attributes": { "color": "red" } }
                """);

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.thingId()).isEqualTo(THING_ID));
        assertThat(rows.stream().map(FlatRow::path)).containsExactlyInAnyOrder("/attributes", "/attributes/color");
    }

    @Test
    public void flattenSearchIndexDocumentOverloadFlattensOnlyThingIgnoringAuthAndBookkeepingFields() {
        final JsonObject thingJson = JsonFactory.newObject("""
                {
                  "thingId": "hello:world",
                  "policyId": "hello:world",
                  "attributes": { "hello": "world" },
                  "features": { "hi": { "properties": { "there": true } } }
                }
                """);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("hello", "world"))
                .forLabel("grant-root")
                .setSubject("g:0", SubjectType.GENERATED)
                .setGrantedPermissions(THING, "/", Permission.READ)
                .build();
        final ThingId thingId = ThingId.of("hello:world");
        final Metadata metadata = Metadata.of(thingId, 1024L, PolicyTag.of(PolicyId.of("hello", "world"), 56L), null,
                Set.of(), null);

        final SearchIndexDocument document = SearchIndexDocumentFactory.create(thingJson, policy, metadata, -1);
        // sanity: the document DOES carry non-empty auth/bookkeeping projections distinct from thing() -- otherwise
        // this test would prove nothing about isolation.
        assertThat(document.policyAuth().isEmpty()).isFalse();
        assertThat(document.globalRead()).isNotEmpty();

        final List<FlatRow> viaDocument = ThingFlattener.flatten(document);
        final List<FlatRow> viaThingDirectly = ThingFlattener.flatten(THING_ID, document.thing());

        // same shape as flattening thing() directly (thingId aside): flatten(document) must derive every row from
        // document.thing() alone, never from policyAuth()/globalRead()/features()[].auth().
        assertThat(viaDocument).hasSameSizeAs(viaThingDirectly);
        assertThat(sortedForCompare(viaDocument)).isEqualTo(sortedForCompare(viaThingDirectly));
        assertThat(rows_pathsOf(viaDocument)).noneMatch(p -> p.toLowerCase().contains("auth"));
    }

    private static List<String> rows_pathsOf(final List<FlatRow> rows) {
        return rows.stream().map(FlatRow::path).toList();
    }

    /**
     * Normalizes thingId away (only path/wpath/fId/ord/typeRank/values matter for this comparison) and sorts, so
     * two row lists produced from the same payload but different thingIds/insertion order can be compared for
     * structural equality.
     */
    private static List<FlatRow> sortedForCompare(final List<FlatRow> rows) {
        return rows.stream()
                .map(r -> new FlatRow("_", r.path(), r.wpath(), r.fId(), r.ord(),
                        r.typeRank(), r.valBool(), r.valNum(), r.valText()))
                .sorted(Comparator.comparing(FlatRow::path)
                        .thenComparing(FlatRow::wpath)
                        .thenComparing(FlatRow::ord))
                .toList();
    }

    // --- rule 9: deterministic emission order ---------------------------------------------------------------------

    @Test
    public void emissionOrderIsDeterministicAcrossRepeatedCalls() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "attributes": { "b": 1, "a": 2 },
                  "features": { "z": { "properties": { "p": 1 } }, "y": { "properties": { "p": 2 } } }
                }
                """);

        final List<FlatRow> first = ThingFlattener.flatten(THING_ID, thing);
        final List<FlatRow> second = ThingFlattener.flatten(THING_ID, thing);

        assertThat(first).isEqualTo(second);
    }

    // --- rule 2: RFC-6901 path escaping (both characters, both "directions" of the ambiguity they resolve) --------

    @Test
    public void rfc6901EscapesTildeInKeys() {
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("a~b", JsonFactory.newValue("v"))
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        assertThat(rowsAtPath(rows, "/attributes/a~0b")).hasSize(1);
        assertThat(rows.stream().map(FlatRow::path)).noneMatch(p -> p.equals("/attributes/a~b"));
    }

    @Test
    public void rfc6901EscapesSlashInKeys() {
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("a/b", JsonFactory.newValue("v"))
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        assertThat(rowsAtPath(rows, "/attributes/a~1b")).hasSize(1);
        // must NOT have been split into two path segments by the literal slash
        assertThat(rows.stream().map(FlatRow::path)).noneMatch(p -> p.equals("/attributes/a") || p.equals("/attributes/a/b"));
    }

    @Test
    public void rfc6901EscapesTildeBeforeSlashSoASlashNeverReEscapesAnIntroducedTilde() {
        // Escaping order matters: '~' MUST be escaped to "~0" before '/' is escaped to "~1". A raw key that is
        // literally the two characters '~','1' must become "~01" (only the tilde escaped), never "~0" + something
        // that double-processes the '1' it introduced. A raw key containing both characters together similarly
        // must escape independently, in one pass.
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("~1", JsonFactory.newValue("literalTildeOne"))
                        .set("~0", JsonFactory.newValue("literalTildeZero"))
                        .set("a~/b", JsonFactory.newValue("combined"))
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        assertThat(onlyRowAtPath(rows, "/attributes/~01").valText()).isEqualTo("literalTildeOne");
        assertThat(onlyRowAtPath(rows, "/attributes/~00").valText()).isEqualTo("literalTildeZero");
        assertThat(onlyRowAtPath(rows, "/attributes/a~0~1b").valText()).isEqualTo("combined");
    }

    @Test
    public void rfc6901EscapingAppliesInsideFeatureSubtreeIncludingTheFeatureIdSegmentItself() {
        // the feature ID segment of the path is escaped exactly like any other key; wpath computation must still
        // correctly locate the "/features/*<rest>" split point even when the feature ID itself needed escaping.
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("a/b", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("x", JsonFactory.newValue(1))
                                        .build())
                                .build())
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        final String escapedPath = "/features/a~1b/properties/x";
        final List<FlatRow> matching = rowsAtPath(rows, escapedPath);
        assertThat(matching).hasSize(2);
        assertThat(matching).allSatisfy(row -> assertThat(row.fId()).isEqualTo("a/b"));
        assertThat(matching.stream().map(FlatRow::wpath))
                .containsExactlyInAnyOrder(escapedPath, "/features/*/properties/x");
    }

    @Test
    public void escapeJsonPointerSegmentIsExposedForThePhaseDTranslatorToMirror() {
        assertThat(ThingFlattener.escapeJsonPointerSegment("a~b")).isEqualTo("a~0b");
        assertThat(ThingFlattener.escapeJsonPointerSegment("a/b")).isEqualTo("a~1b");
        assertThat(ThingFlattener.escapeJsonPointerSegment("plain")).isEqualTo("plain");
    }

    // --- unicode keys -----------------------------------------------------------------------------------------

    @Test
    public void unicodeKeysAreCarriedThroughUnescapedAsideFromRfc6901Characters() {
        final JsonObject thing = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("café", JsonFactory.newValue("straße"))
                        .set("日本語", JsonFactory.newValue(true))
                        .build())
                .build();

        final List<FlatRow> rows = ThingFlattener.flatten(THING_ID, thing);

        assertThat(onlyRowAtPath(rows, "/attributes/café").valText()).isEqualTo("straße");
        assertThat(onlyRowAtPath(rows, "/attributes/日本語").valBool()).isTrue();
    }

    // --- integration: a real Thing + Policy via SearchIndexDocumentFactory ---------------------------------------

    @Test
    public void integrationCaseViaSearchIndexDocumentFactoryFromRealThingAndPolicy() {
        final JsonObject thingJson = JsonFactory.newObject("""
                {
                  "thingId": "bosch:device",
                  "policyId": "bosch:device-policy",
                  "attributes": {
                    "location": { "latitude": 44.673856, "longitude": 8.261719 }
                  },
                  "features": {
                    "accelerometer": {
                      "definition": [ "bosch:accelerometer:1.2.3" ],
                      "properties": { "x": 3.141 }
                    },
                    "distance": {
                      "definition": [ "bosch:distance-sensor:4.1.0" ],
                      "properties": { "d": 2.71828 }
                    }
                  }
                }
                """);

        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "bosch:device-policy",
                  "revision": 2,
                  "entries": {
                    "global": {
                      "subjects": { "issuer:global": {"type":"default"} },
                      "resources": { "thing:/": {"grant": ["READ"],"revoke": []} }
                    }
                  }
                }
                """);

        final ThingId thingId = ThingId.of("bosch:device");
        final Metadata metadata = Metadata.of(thingId, 111L,
                PolicyTag.of(PolicyId.of("bosch:device-policy"), 2L), null, Set.of(), null);

        final SearchIndexDocument document = SearchIndexDocumentFactory.create(thingJson, policy, metadata, -1);

        final List<FlatRow> rows = ThingFlattener.flatten(document);

        assertThat(rows).allSatisfy(row -> assertThat(row.thingId()).isEqualTo("bosch:device"));

        final FlatRow latRow = onlyRowAtPath(rows, "/attributes/location/latitude");
        assertThat(latRow.fId()).isNull();
        assertThat(latRow.typeRank()).isEqualTo(ThingFlattener.TYPE_NUMBER);
        assertThat(latRow.valNum()).isEqualByComparingTo("44.673856");

        final List<FlatRow> xRows = rowsAtPath(rows, "/features/accelerometer/properties/x");
        assertThat(xRows).hasSize(2);
        assertThat(xRows).allSatisfy(row -> {
            assertThat(row.fId()).isEqualTo("accelerometer");
            assertThat(row.valNum()).isEqualByComparingTo("3.141");
        });
        assertThat(xRows.stream().map(FlatRow::wpath))
                .containsExactlyInAnyOrder("/features/accelerometer/properties/x", "/features/*/properties/x");

        // "definition" is a non-empty, non-nested array of strings under a feature -> exploded elements, dual-row.
        final List<FlatRow> definitionRows = rowsAtPath(rows, "/features/distance/definition");
        assertThat(definitionRows).hasSize(2); // one element * dual-wpath
        assertThat(definitionRows).allSatisfy(row -> {
            assertThat(row.fId()).isEqualTo("distance");
            assertThat(row.typeRank()).isEqualTo(ThingFlattener.TYPE_STRING);
            assertThat(row.valText()).isEqualTo("bosch:distance-sensor:4.1.0");
        });
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private static List<FlatRow> rowsAtPath(final List<FlatRow> rows, final String path) {
        return rows.stream().filter(r -> r.path().equals(path)).toList();
    }

    private static FlatRow onlyRowAtPath(final List<FlatRow> rows, final String path) {
        final List<FlatRow> matching = rowsAtPath(rows, path);
        assertThat(matching).as("rows at path %s", path).hasSize(1);
        return matching.get(0);
    }

}
