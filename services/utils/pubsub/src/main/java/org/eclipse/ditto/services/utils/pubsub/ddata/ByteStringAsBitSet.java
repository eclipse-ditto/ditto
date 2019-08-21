/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import java.util.stream.Stream;

import akka.util.ByteString;

/**
 * View on a byte string as a set over all integers.
 */
final class ByteStringAsBitSet {

    private final ByteString byteString;

    private ByteStringAsBitSet(final ByteString byteString) {
        this.byteString = byteString;
    }

    /**
     * Test if a byte string contains every integer in a collection when interpreted as a set over all integers.
     *
     * @param byteString the byte string.
     * @param element the collection of integers.
     * @return whether all integers are in the set interpretation of the byte string.
     */
    public static boolean contains(final ByteString byteString, final Stream<Integer> element) {
        return new ByteStringAsBitSet(byteString).contains(element);
    }

    /**
     * Test if a byte string contains all members of some group of integers when interpreted as a set over all integers.
     *
     * @param byteString the byte string.
     * @param elementGroups the element groups.
     * @return whether all integers of some group are in the set interpretation of the byte string.
     */
    public static boolean containsAny(final ByteString byteString, final Stream<Stream<Integer>> elementGroups) {

        return new ByteStringAsBitSet(byteString).containsAny(elementGroups);
    }

    /**
     * Construct a byte string with all bits in the collections set.
     *
     * @param numberOfBytes size of the byte string in bytes.
     * @param bitIndices which bits are set.
     * @return the byte string.
     */
    public static ByteString construct(final int numberOfBytes, final Stream<Integer> bitIndices) {
        final byte[] byteArray = new byte[numberOfBytes];
        bitIndices.forEach(bitIndex -> {
            final ByteIndexAndMask indexAndMask = ByteIndexAndMask.of(numberOfBytes, bitIndex);
            byteArray[indexAndMask.byteIndex] |= indexAndMask.byteMask;
        });
        // this byte array is visible for no one else. safe to construct byte string from it without copying.
        return ByteString.fromArrayUnsafe(byteArray);
    }

    private boolean contains(final Stream<Integer> element) {
        return element.allMatch(this::getBit);
    }

    private boolean containsAny(final Stream<Stream<Integer>> elementGroups) {
        return elementGroups.anyMatch(this::contains);
    }

    private boolean getBit(int bitIndex) {
        // get non-negative indices treating bitIndex as unsigned int
        final ByteIndexAndMask indexAndMask = ByteIndexAndMask.of(byteString.length(), bitIndex);
        final byte theByte = byteString.apply(indexAndMask.byteIndex);
        return (theByte & indexAndMask.byteMask) != 0;
    }

    private static final class ByteIndexAndMask {

        final int byteIndex;
        final int byteMask;

        private ByteIndexAndMask(final int byteIndex, final int byteMask) {
            this.byteIndex = byteIndex;
            this.byteMask = byteMask;
        }

        private static ByteIndexAndMask of(final int numberOfBytes, final int bitIndex) {
            final int wrapOverByteIndex = (bitIndex >> 3) & 0x1fffffff;
            final int bitInByte = bitIndex & 0x7;
            final int byteMask = 0x1 << bitInByte;
            return new ByteIndexAndMask(wrapOverByteIndex % numberOfBytes, byteMask);
        }
    }
}
