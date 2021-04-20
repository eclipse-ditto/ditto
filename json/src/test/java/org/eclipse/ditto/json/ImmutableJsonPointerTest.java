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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ImmutableJsonPointer}.
 */
public final class ImmutableJsonPointerTest {

    private static final JsonKey KNOWN_KEY_NAME = JsonFactory.newKey("foo");

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonPointer.class,
                areImmutable(),
                provided(JsonKey.class).isAlsoImmutable(), assumingFields("jsonFields").areNotModifiedAndDoNotEscape());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonPointer.class)
                .suppress(Warning.NULL_FIELDS)
                .verify();
    }

    @Test
    public void createInstanceFromStringWithLeadingSlash() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/foo/bar/baz");
        final byte expectedLevelCount = 3;

        assertThat(underTest).hasLevelCount(expectedLevelCount);
    }

    @Test
    public void createInstanceFromSlash() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/");
        final byte expectedLevelCount = 0;

        assertThat(underTest).hasLevelCount(expectedLevelCount);
        assertThat(underTest).isEmpty();
    }

    @Test
    public void emptyPointerEqualsPointerWithOneSlash() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/");

        assertThat(underTest).isEqualTo(ImmutableJsonPointer.empty());
        assertThat(underTest.toString()).isEqualTo(ImmutableJsonPointer.empty().toString());
    }

    @Test
    public void createInstanceFromStringWithTrailingSlash() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/foo/bar/baz/");
        final byte expectedLevelCount = 3;

        assertThat(underTest).hasLevelCount(expectedLevelCount);
    }

    @Test
    public void createInstanceFromStringWithTwoDelimitingSlashes() {
        this.assertConsecutiveSlashesExceptionFor("/foo//bar/baz/");
    }

    @Test
    public void createInstanceFromStringWithTwoStartingSlashes() {
        this.assertConsecutiveSlashesExceptionFor("//foo/bar/baz/");
    }

    @Test
    public void createInstanceFromStringWithTwoEndingSlashes() {
        this.assertConsecutiveSlashesExceptionFor("/foo/bar/baz//");
    }

    private void assertConsecutiveSlashesExceptionFor(final String jsonPointer) {
        assertThatExceptionOfType(JsonPointerInvalidException.class)
                .isThrownBy(() -> ImmutableJsonPointer.ofParsed(jsonPointer))
                .withMessageContaining(jsonPointer)
                .satisfies(e -> assertThat(e.getDescription()).contains("Consecutive slashes in JSON pointers are not supported."));
    }

    @Test
    public void getReturnsExpected() {
        final String secondLevelKeyName = "bar";
        final JsonKey secondLevelJsonKey = JsonFactory.newKey(secondLevelKeyName);

        final JsonPointer underTest = ImmutableJsonPointer.of(KNOWN_KEY_NAME, secondLevelJsonKey);

        assertThat(underTest.get(0)).contains(KNOWN_KEY_NAME);
        assertThat(underTest.get(1)).contains(secondLevelJsonKey);
    }

    @Test
    public void getLevelsCountReturnsExpected() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/foo/bar/baz");

        assertThat(underTest.getLevelCount()).isEqualTo(3);
    }

    @Test(expected = NullPointerException.class)
    public void tryToParseNullString() {
        ImmutableJsonPointer.ofParsed(null);
    }

    @Test
    public void parseSlashDelimitedStringToGetAJsonPointerInstance() {
        final String firstLevel = "first";
        final String secondLevel = "second";
        final String thirdLevel = "third";

        // The trailing slash is only there to prove some resilience of the parse method.
        final String slashDelimited = "/" + firstLevel + "/" + secondLevel + "/" + thirdLevel + "/";
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed(slashDelimited);

        assertThat(underTest.getLevelCount()).isEqualTo(3);
    }

    @Test
    public void iterationWorksAsExpected() {
        final JsonKey first = JsonFactory.newKey("first");
        final JsonKey second = JsonFactory.newKey("second");
        final JsonKey third = JsonFactory.newKey("third");

        final List<JsonKey> knownLevels = Arrays.asList(first, second, third);
        final List<JsonKey> levelsFromIteration = new ArrayList<>();

        final JsonPointer underTest = ImmutableJsonPointer.of(first, second, third);
        for (final JsonKey jsonKey : underTest) {
            levelsFromIteration.add(jsonKey);
        }

        assertThat(levelsFromIteration).containsExactly(knownLevels.toArray(new JsonKey[knownLevels.size()]));
    }

    @Test(expected = IllegalStateException.class)
    public void iteratorIsImmutable() {
        final JsonKey first = JsonFactory.newKey("first");
        final JsonKey second = JsonFactory.newKey("second");
        final JsonKey third = JsonFactory.newKey("third");

        final JsonPointer underTest = ImmutableJsonPointer.of(first, second, third);

        final Iterator<JsonKey> iterator = underTest.iterator();
        while (iterator.hasNext()) {
            iterator.remove();
        }
    }

    @Test
    public void addingNewLevelReturnsNewJsonPointer() {
        final JsonPointer originalJsonPointer = ImmutableJsonPointer.ofParsed("/foo/bar");

        assertThat(originalJsonPointer.getLevelCount()).isEqualTo(2);

        final JsonPointer extendedJsonPointer = originalJsonPointer.addLeaf(JsonFactory.newKey("baz"));

        assertThat(extendedJsonPointer.getLevelCount()).isEqualTo(3);
        assertThat(extendedJsonPointer).isNotEqualTo(originalJsonPointer);
    }

    @Test
    public void tryToAddNullLeaf() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/foo/bar");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addLeaf(null))
                .withMessage("The %s must not be null!", "level to be added")
                .withNoCause();
    }

    @Test
    public void createInstanceFromRootPointer() {
        final String rootString = "/";
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed(rootString);

        assertThat(underTest.isEmpty()).isTrue();
        assertThat(underTest.toString()).isEqualTo(rootString);
    }

    @Test
    public void getSubPointerWithNegativeLevelNumber() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/foo/bar/baz");
        final Optional<JsonPointer> subPointer = underTest.getSubPointer(-1);

        assertThat(subPointer).isEmpty();
    }

    @Test
    public void getSubPointerWithTooHighLevelNumberReturnsEmptyOptional() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/foo/bar/baz");
        final byte levelNumber = 23;
        final Optional<JsonPointer> subPointer = underTest.getSubPointer(levelNumber);

        assertThat(subPointer).isEmpty();
    }

    @Test
    public void getSubPointerReturnsExpected() {
        final JsonPointer underTest = ImmutableJsonPointer.ofParsed("/foo/bar/baz/oogle/foogle");
        final byte levelNumber = 3;
        final Optional<JsonPointer> actual = underTest.getSubPointer(levelNumber);
        final JsonPointer expected = ImmutableJsonPointer.ofParsed("/oogle/foogle");

        assertThat(actual).contains(expected);
    }

    @Test
    public void createPointerWithOrWithoutLeadingSlashIsTheSame() {
        final JsonPointer without = ImmutableJsonPointer.ofParsed("foo/bar");
        final JsonPointer with = ImmutableJsonPointer.ofParsed("/foo/bar");

        assertThat(without).isEqualTo(with);
    }

    @Test
    public void cutLeafWorksAsExpected() {
        final JsonPointer jsonPointer = ImmutableJsonPointer.ofParsed("/foo/bar/baz/oogle/foogle");
        final JsonPointer underTest = jsonPointer.cutLeaf();
        final JsonKey expectedNewLeaf = JsonFactory.newKey("oogle");

        assertThat(underTest.getLeaf()).contains(expectedNewLeaf);
    }

    @Test
    public void appendSubPointerReturnsExpected() {
        final JsonPointer root = ImmutableJsonPointer.of(JsonFactory.newKey("root"));
        final JsonPointer subPointer = ImmutableJsonPointer.of(JsonFactory.newKey("child"), JsonFactory.newKey("sub"));
        final JsonPointer newPointer = root.append(subPointer);

        assertThat(newPointer.toString()).isEqualTo("/root/child/sub");
    }

    @Test
    public void appendEmptySubPointerReturnsSamePointer() {
        final JsonPointer root = ImmutableJsonPointer.of(JsonFactory.newKey("root"));
        final ImmutableJsonPointer empty = ImmutableJsonPointer.empty();
        final JsonPointer newPointer = root.append(empty);

        assertThat(newPointer).isSameAs(root);
    }

    @Test
    public void createInstanceFromParsedStringWithEscapedTildesWorksAsExpected() {
        final String key1 = "foo";
        final String key2 = "~0dum~0die~0dum";
        final String key3 = "baz";

        final String pointerString = "/" + key1 + "/" + key2 + "/" + key3;

        final JsonPointer underTest = ImmutableJsonPointer.ofParsed(pointerString);

        assertThat(underTest).hasLevelCount(3);
        assertThat(underTest.get(1)).contains(JsonFactory.newKey("~dum~die~dum"));
        assertThat(underTest.toString()).isEqualTo("/foo/~0dum~0die~0dum/baz");
    }

    @Test
    public void createInstanceFromJsonKeysWithEscapedTildesAndSlashesWorksAsExpected() {
        final JsonKey key1 = JsonFactory.newKey("foo");
        final JsonKey key2 = JsonFactory.newKey("~dum/~die/~dum");
        final JsonKey key3 = JsonFactory.newKey("baz");

        final JsonPointer underTest = ImmutableJsonPointer.of(key1, key2, key3);

        assertThat(underTest).hasLevelCount(3);
        assertThat(underTest.get(1)).contains(key2);
        assertThat(underTest.toString()).isEqualTo("/foo/~0dum/~0die/~0dum/baz");
    }

}
