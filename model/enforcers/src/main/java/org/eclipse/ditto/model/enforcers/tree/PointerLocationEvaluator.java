/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.enforcers.tree;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

/**
 * This function is based on a JSON pointer as reference. It determines the relative location of any other JSON
 * pointer to that reference pointer.
 */
@Immutable
final class PointerLocationEvaluator implements Function<JsonPointer, PointerLocation> {

    private final JsonPointer referencePointer;

    /**
     * Constructs a new {@code PointerLocationEvaluator} object.
     *
     * @param referencePointer the pointer acts as reference for each pointer which is evaluated by
     * {@link #apply(JsonPointer)}.
     * @throws NullPointerException if {@code referencePointer} is {@code null}.
     */
    PointerLocationEvaluator(final JsonPointer referencePointer) {
        this.referencePointer = checkNotNull(referencePointer, "reference pointer");
    }

    @Override
    public PointerLocation apply(final JsonPointer evaluationPointer) {
        checkNotNull(evaluationPointer, "pointer to be evaluated");

        PointerLocation result = PointerLocation.DIFFERENT;
        if (isOnReferencePointer(evaluationPointer)) {
            final int evaluationPointerLevelCount = evaluationPointer.getLevelCount();
            final int referencePointerLevelCount = referencePointer.getLevelCount();
            if (evaluationPointerLevelCount < referencePointerLevelCount) {
                result = PointerLocation.ABOVE;
            } else if (evaluationPointerLevelCount > referencePointerLevelCount) {
                result = PointerLocation.BELOW;
            } else {
                result = PointerLocation.SAME;
            }
        }
        return result;
    }

    private boolean isOnReferencePointer(final JsonPointer evaluationPointer) {
        boolean result = true;

        final int referencePointerLevelCount = referencePointer.getLevelCount();
        final int evaluationPointerLevelCount = evaluationPointer.getLevelCount();
        int currentLevel = 0;
        while (result &&
                currentLevel < referencePointerLevelCount &&
                currentLevel < evaluationPointerLevelCount) {

            final JsonKey referencePointerCurrentLevelKey = referencePointer.get(currentLevel).orElse(null);
            result = evaluationPointer.get(currentLevel)
                    .filter(k -> k.equals(referencePointerCurrentLevelKey))
                    .isPresent();
            currentLevel++;
        }

        return result;
    }

}
