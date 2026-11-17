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
package org.eclipse.ditto.wot.api.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Tests the calculation of the leaves affected by a merge patch in {@link DefaultWotThingModelValidator}.
 */
public final class DefaultWotThingModelValidatorMergeLeavesTest {

    @Test
    public void leavesOfModifiedFields() {
        assertThat(leaves("/", "{\"attributes\":{\"a\":1,\"b\":{\"c\":true}}}"))
                .containsExactlyInAnyOrder(JsonPointer.of("/attributes/a"), JsonPointer.of("/attributes/b/c"));
        assertThat(leaves("/attributes/a", "1")).containsExactly(JsonPointer.of("/attributes/a"));
    }

    @Test
    public void deletedFieldsAreLeaves() {
        assertThat(leaves("/", "{\"attributes\":null,\"features\":{\"f\":null}}"))
                .containsExactlyInAnyOrder(JsonPointer.of("/attributes"), JsonPointer.of("/features/f"));
        assertThat(leaves("/attributes", "{\"a\":null}")).containsExactly(JsonPointer.of("/attributes/a"));
        assertThat(leaves("/attributes/a", "null")).containsExactly(JsonPointer.of("/attributes/a"));
    }

    @Test
    public void regexDeletionWithBothDelimitersAffectsEnclosingObject() {
        assertThat(leaves("/", "{\"attributes\":{\"{{ ~.*~ }}\":null}}"))
                .containsExactly(JsonPointer.of("/attributes"));
        assertThat(leaves("/", "{\"attributes\":{\"{{ /.*/ }}\":null}}"))
                .containsExactly(JsonPointer.of("/attributes"));
        assertThat(leaves("/features/f/properties", "{\"{{ ~.*~ }}\":null,\"a\":1}"))
                .containsExactlyInAnyOrder(JsonPointer.of("/features/f/properties"),
                        JsonPointer.of("/features/f/properties/a"));
    }

    @Test
    public void regexDeletionAsLastPathSegmentAffectsEnclosingObject() {
        assertThat(leaves("/attributes/{{ ~.*~ }}", "null")).containsExactly(JsonPointer.of("/attributes"));
    }

    @Test
    public void emptyObjectsAreLeaves() {
        assertThat(leaves("/attributes", "{\"firmware\":{}}")).containsExactly(JsonPointer.of("/attributes/firmware"));
        assertThat(leaves("/attributes/firmware", "{}")).containsExactly(JsonPointer.of("/attributes/firmware"));
        assertThat(leaves("/", "{\"attributes\":{\"a\":1,\"b\":{}}}"))
                .containsExactlyInAnyOrder(JsonPointer.of("/attributes/a"), JsonPointer.of("/attributes/b"));
        assertThat(leaves("/", "{\"features\":{\"f\":{\"properties\":{}}}}"))
                .containsExactly(JsonPointer.of("/features/f/properties"));
    }

    @Test
    public void onlyEmptyMergePatchAtThingLevelIsEmptyObject() {
        assertThat(DefaultWotThingModelValidator.isEmptyObject(
                DefaultWotThingModelValidator.toMergePatch(JsonPointer.empty(), JsonFactory.readFrom("{}")))).isTrue();
        assertThat(DefaultWotThingModelValidator.isEmptyObject(
                DefaultWotThingModelValidator.toMergePatch(JsonPointer.of("/attributes"), JsonFactory.readFrom("{}"))))
                .isFalse();
        assertThat(DefaultWotThingModelValidator.isEmptyObject(JsonValue.nullLiteral())).isFalse();
    }

    @Test
    public void deletingAllFeaturesOrAllRootFieldsAffectsWholeThing() {
        assertThat(DefaultWotThingModelValidator.affectsWholeThingOrAllFeatures(leaves("/", "{\"features\":null}")))
                .isTrue();
        assertThat(DefaultWotThingModelValidator.affectsWholeThingOrAllFeatures(
                leaves("/features", "{\"{{ ~.*~ }}\":null}"))).isTrue();
        assertThat(DefaultWotThingModelValidator.affectsWholeThingOrAllFeatures(leaves("/", "{\"{{ ~.*~ }}\":null}")))
                .isTrue();
    }

    @Test
    public void modifyingSingleFeatureOrAttributesDoesNotAffectWholeThing() {
        assertThat(DefaultWotThingModelValidator.affectsWholeThingOrAllFeatures(
                leaves("/", "{\"features\":{\"f\":null},\"attributes\":null}"))).isFalse();
    }

    private static Set<JsonPointer> leaves(final String path, final String value) {
        final JsonValue mergePatch =
                DefaultWotThingModelValidator.toMergePatch(JsonPointer.of(path), JsonFactory.readFrom(value));
        return DefaultWotThingModelValidator.calculateLeaves(JsonPointer.empty(), mergePatch);
    }

}
