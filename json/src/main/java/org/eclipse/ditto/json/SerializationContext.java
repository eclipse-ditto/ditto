/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Bundles state and configuration for serialization. Must be recreated for each serialization target.
 *
 * <p>
 * <b>This is a Ditto internal class which is not intended for re-use.</b>
 * It therefore is not treated as API which is held binary compatible to previous versions.
 * </p>
 *
 * @since 1.2.1
 */
public interface SerializationContext extends Closeable, Flushable {

    /**
     * Allows the caller to directly embed cached data in the Buffer.
     * This can only be used to write exactly one element.
     *
     * @param cachedData The data to write in an appropriately sized array.
     */
    void writeCachedElement(byte[] cachedData) throws IOException;

    /**
     * Writes {@code null} to the serialization context.
     */
    void writeNull() throws IOException;

    /**
     * Writes the passed boolean {@code state} to the serialization context.
     */
    void writeBoolean(boolean state) throws IOException;

    /**
     * Writes the passed float {@code number} to the serialization context.
     */
    void writeNumber(float number) throws IOException;

    /**
     * Writes the passed double {@code number} to the serialization context.
     */
    void writeNumber(double number) throws IOException;

    /**
     * Writes the passed long {@code number} to the serialization context.
     */
    void writeNumber(long number) throws IOException;

    /**
     * Writes the passed long {@code number} to the serialization context.
     */
    void writeNumber(int number) throws IOException;

    /**
     * Writes the passed string {@code text} to the serialization context.
     */
    void writeString(String text) throws IOException;

    /**
     * Writes the passed string {@code name} (as field name) to the serialization context.
     */
    void writeFieldName(String name) throws IOException;
}
