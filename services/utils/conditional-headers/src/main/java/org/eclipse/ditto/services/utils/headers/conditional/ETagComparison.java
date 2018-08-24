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

import javax.annotation.Nullable;

/**
 * Implements etag comparison based on <a href="https://tools.ietf.org/html/rfc7232#section-2.3.2">RFC 7232</a>
 */
public final class ETagComparison {

    private ETagComparison() {}

    public static boolean strong(@Nullable final String eTagValue1, @Nullable final String etagValue2) {
        if (eTagValue1 == null) {
            return false;
        }

        if (etagValue2 == null) {
            return false;
        }

        final EntityTag entityTag1 = EntityTag.fromString(eTagValue1);
        final EntityTag entityTag2 = EntityTag.fromString(etagValue2);

        if (entityTag1.isWeak()) {
            return false;
        }

        if (entityTag2.isWeak()) {
            return false;
        }

        return entityTag1.equals(entityTag2);
    }

    public static boolean weak(@Nullable final String eTagValue1, @Nullable final String etagValue2) {
        if (eTagValue1 == null) {
            return false;
        }

        if (etagValue2 == null) {
            return false;
        }

        final EntityTag entityTag1 = EntityTag.fromString(eTagValue1);
        final EntityTag entityTag2 = EntityTag.fromString(etagValue2);

        return entityTag1.getOpaqueTag().equals(entityTag2.getOpaqueTag());
    }
}
