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
package org.eclipse.ditto.protocol;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.messages.model.MessageDirection;

/**
 * Immutable implementation of {@link org.eclipse.ditto.protocol.MessagePath}.
 */
@Immutable
final class ImmutableMessagePath implements MessagePath {

    private static final JsonKey FEATURES = JsonKey.of("features");

    private final JsonPointer jsonPointer;

    private ImmutableMessagePath(final JsonPointer jsonPointer) {
        this.jsonPointer = jsonPointer;
    }

    /**
     * Interpret a JSON pointer as message path.
     *
     * @param jsonPointer the JSON pointer to interpret.
     * @return the interpreted message path.
     */
    public static MessagePath of(final JsonPointer jsonPointer) {
        checkNotNull(jsonPointer, "JSON pointer");
        return new ImmutableMessagePath(jsonPointer);
    }

    @Override
    public Optional<String> getFeatureId() {
        return jsonPointer.getRoot()
                .filter(FEATURES::equals)
                .map(features -> jsonPointer.nextLevel())
                .flatMap(JsonPointer::getRoot)
                .map(JsonKey::toString);
    }

    @Override
    public Optional<MessageDirection> getDirection() {
        return jsonPointer.getRoot()
                .flatMap(MessagePath::jsonKeyToDirection)
                .map(Optional::of)
                .orElseGet(() -> jsonPointer.getRoot()
                        .filter(FEATURES::equals)
                        .flatMap(features -> jsonPointer.get(2))
                        .flatMap(MessagePath::jsonKeyToDirection));
    }

    @Override
    public JsonPointer addLeaf(final JsonKey key) {
        return jsonPointer.addLeaf(key);
    }

    @Override
    public JsonPointer append(final JsonPointer pointer) {
        return jsonPointer.append(pointer);
    }

    @Override
    public int getLevelCount() {
        return jsonPointer.getLevelCount();
    }

    @Override
    public boolean isEmpty() {
        return jsonPointer.isEmpty();
    }

    @Override
    public Optional<JsonKey> get(final int level) {
        return jsonPointer.get(level);
    }

    @Override
    public Optional<JsonKey> getRoot() {
        return jsonPointer.getRoot();
    }

    @Override
    public Optional<JsonKey> getLeaf() {
        return jsonPointer.getLeaf();
    }

    @Override
    public JsonPointer cutLeaf() {
        return jsonPointer.cutLeaf();
    }

    @Override
    public JsonPointer nextLevel() {
        return jsonPointer.nextLevel();
    }

    @Override
    public Optional<JsonPointer> getSubPointer(final int level) {
        return jsonPointer.getSubPointer(level);
    }

    @Override
    public Optional<JsonPointer> getPrefixPointer(final int level) {
        return jsonPointer.getPrefixPointer(level);
    }

    @Override
    public JsonFieldSelector toFieldSelector() {
        return jsonPointer.toFieldSelector();
    }

    @Override
    public int length() {
        return jsonPointer.length();
    }

    @Override
    public char charAt(final int index) {
        return jsonPointer.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return jsonPointer.subSequence(start, end);
    }

    @Override
    public Iterator<JsonKey> iterator() {
        return jsonPointer.iterator();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableMessagePath that = (ImmutableMessagePath) o;
        return Objects.equals(jsonPointer, that.jsonPointer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonPointer);
    }

    @Override
    public String toString() {
        return jsonPointer.toString();
    }
}
