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
package org.eclipse.ditto.internal.utils.pubsub.ddata;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionException;

import scala.util.hashing.MurmurHash3$;

/**
 * Mixin for a family of Murmur-3 hash functions. Also provides SHA-256 digest for strings as a means to seed the hash
 * family by a string.
 */
public interface Hashes {

    /**
     * Hash family size is fixed at 2 to fit hash code into a long int.
     */
    int HASH_FAMILY_SIZE = 2;

    /**
     * Get seeds for the family of hash functions.
     *
     * @return the seeds.
     */
    Collection<Integer> getSeeds();

    /**
     * Hash a string by a family of hash functions.
     *
     * @param string the string to hash.
     * @return a list of hash codes.
     */
    default List<Integer> getHashes(final String string) {
        return getSeeds().stream().map(seed -> murmurHash(string, seed)).toList();
    }

    /**
     * Hash a string topic into a long integer.
     *
     * @param topic the topic.
     * @return the hashed topic.
     */
    default Long hashAsLong(final String topic) {
        final List<Integer> hashes = getHashes(topic);
        return ((long) hashes.get(0)) << 32 | hashes.get(1) & 0xffffffffL;
    }

    /**
     * Hash a string by a seeded Murmur-3 hash function.
     *
     * @param string the string to hash.
     * @param seed the seed.
     * @return the hash code.
     */
    static int murmurHash(final String string, final int seed) {
        return MurmurHash3$.MODULE$.stringHash(string, seed);
    }

    /**
     * Create pseudorandom integers from a string seed by SHA-256. Iteratively digest the previous digest to get more
     * bits if they run out.
     *
     * @param input the initial seed.
     * @param howManyIntegers how many pseudorandom integers to get.
     * @return list of integers.
     */
    static List<Integer> digestStringsToIntegers(final String input, final int howManyIntegers) {
        // how many integers are in one SHA-256 digest
        final int batchSize = 256 / 32;
        final List<Integer> arrayList = new ArrayList<>(howManyIntegers);
        final MessageDigest sha256 = getSha256();
        byte[] nextInput = input.getBytes();
        for (int remainingSize = howManyIntegers; remainingSize > 0; remainingSize -= batchSize) {
            final int thisBatchSize = Math.min(remainingSize, batchSize);
            final byte[] digest = sha256.digest(nextInput);
            final ByteBuffer byteBuffer = ByteBuffer.wrap(digest);
            for (int i = 0; i < thisBatchSize; ++i) {
                arrayList.add(byteBuffer.getInt());
            }
            nextInput = digest;
            sha256.reset();
        }
        return arrayList;
    }

    /**
     * Get a SHA-256 implementation.
     *
     * @return the implementation.
     */
    static MessageDigest getSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            // impossible - all JVM must support SHA-256.
            throw new CompletionException(e);
        }
    }
}
