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

package org.eclipse.ditto.services.utils.headers.conditional;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

public class EntityTag {

    private final boolean weak;

    private final String opaqueTag;

    private EntityTag(final boolean weak, final String opaqueTag) {
        validateOpaqueTag(opaqueTag);
        this.weak = weak;
        this.opaqueTag = opaqueTag;
    }

    private static void validateOpaqueTag(final String opaqueTag) {
        if (!opaqueTag.matches("\"[^\"]*\"")) {
            throw OpaqueTagInvalidException.newBuilder(opaqueTag).build();
        }
    }

    public String getOpaqueTag() {
        return opaqueTag;
    }

    public boolean isWeak() {
        return weak;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EntityTag entityTag = (EntityTag) o;
        return weak == entityTag.weak &&
                Objects.equals(opaqueTag, entityTag.opaqueTag);
    }

    @Override
    public int hashCode() {

        return Objects.hash(weak, opaqueTag);
    }

    static EntityTag fromString(final String entityTag) {
        checkNotNull(entityTag);
        final boolean weak = isWeak(entityTag);
        final String opaqueTag = extractOpaqueTag(entityTag, weak);

        return new EntityTag(weak, opaqueTag);
    }


    private static boolean isWeak(final String entityTag) {
        return entityTag.startsWith("W/");
    }

    private static String extractOpaqueTag(final String entityTag, final boolean weak) {
        if (weak) {
            return entityTag.substring(2);
        } else {
            return entityTag;
        }
    }
}
