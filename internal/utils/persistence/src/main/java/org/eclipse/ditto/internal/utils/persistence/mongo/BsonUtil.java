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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import com.mongodb.MongoClientSettings;

/**
 * Utility methods for criteria creation.
 */
@Immutable
public final class BsonUtil {

    private static final String NULL_STRING = Objects.toString(null);

    private static final CodecRegistry CODEC_REGISTRY = MongoClientSettings.builder().build().getCodecRegistry();

    private BsonUtil() {
        throw new AssertionError();
    }

    /**
     * Converts the given {@link Bson} object to a {@link BsonDocument}.
     *
     * @param bsonObj the Bson object.
     * @return the Bson document.
     * @throws NullPointerException if {@code bsonObj} is {@code null}.
     */
    public static BsonDocument toBsonDocument(final Bson bsonObj) {
        checkNotNull(bsonObj, "BSON object to be converted");
        return bsonObj.toBsonDocument(BsonDocument.class, CODEC_REGISTRY);
    }

    /**
     * Converts the given {@link Bson} object to a {@link BsonDocument}, if it is not {@code null}. Returns {@code
     * null} otherwise.
     *
     * @param bsonObj the Bson object or {@code null}.
     * @return the Bson document or {@code null}.
     */
    public static @Nullable
    BsonDocument toBsonDocumentOrNull(@Nullable final Bson bsonObj) {
        if (bsonObj == null) {
            return null;
        }

        return toBsonDocument(bsonObj);
    }

    /**
     * Converts the given collection of {@link Bson} objects to a List of {@link BsonDocument}s.
     *
     * @param bsonObjs the Bson objects.
     * @return the Bson documents.
     * @throws NullPointerException if {@code bsonObjs} is {@code null}.
     */
    public static List<BsonDocument> toBsonDocuments(final Collection<Bson> bsonObjs) {
        checkNotNull(bsonObjs, "BSON objects to be converted");
        return bsonObjs.stream()
                .map(BsonUtil::toBsonDocument)
                .toList();
    }

    /**
     * Returns the value to which the specified key is mapped. The value is cast to type {@code <T>}.
     *
     * @param document the document whose value is requested
     * @param key the key which holds the value
     * @param clazz the class to cast the value to
     * @param <T> the type to which the value will be cast.
     * @return the value.
     * @throws ClassCastException if the value has not the expected type
     * @throws NullPointerException if this document contains no mapping for the key
     * @see Document#get(Object)
     */
    public static <T> T getRequiredDocumentValueAt(final Document document, final String key, final Class<T> clazz) {
        final T value = getDocumentValueOrNullAt(document, key, clazz);
        if (value == null) {
            throw new NullPointerException("Key not found: " + key);
        }
        return value;
    }

    /**
     * Returns the value to which the specified key is mapped or the specified {@code defaultValue}.
     * The value is cast to type {@code <T>}.
     *
     * <p>
     * <strong>NOTE:</strong> Method {@link Document#get(Object, Object)} does the same, but throws a
     * {@link ClassCastException} if the {@code defaultValue} is not exactly the same type as {@code <T>}, which
     * happens quite often: E.g., you want to define {@code <T>} as {@link Collection} and {@code defaultValue}
     * as {@link Collections#emptyList()}, which is a subtype of {@link Collection}.
     * </p>
     *
     * @param document the document whose value is requested
     * @param key the key which holds the value
     * @param clazz the class to cast the value to
     * @param defaultValue the default value
     * @param <T> the type to which the value will be cast.
     * @return the value.
     * @throws ClassCastException if the value has not the expected type
     * @throws NullPointerException if this document contains no mapping for the key
     * @see Document#get(Object)
     */
    public static <T> T getDocumentWithDefaultAt(final Document document, final String key, final Class<T>
            clazz, final T defaultValue) {
        final T value = getDocumentValueOrNullAt(document, key, clazz);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Allows to extract values from {@link Bson} objects by specifying a dot-separated path,
     * e.g. {@code thing.policyId}.
     *
     * <p>NOTE: Arrays are currently not supported.</p>
     *
     * @param bsonObj the Bson object.
     * @param path the path.
     * @param <T> the type to which the value will be cast.
     * @return the value or {@code null}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws ClassCastException if the value has not the expected type.
     */
    @Nullable
    public static <T> T getValueByPath(final Bson bsonObj, final String path) {
        checkNotNull(bsonObj, "BSON object which provides the value");
        checkNotNull(path, "path");

        final BsonDocument doc = toBsonDocument(bsonObj);
        final List<String> paths = Arrays.asList(path.split("\\."));
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Empty path not allowed");
        }

        final String topLevelKey = paths.get(0);
        final String remainingPath = String.join(".", paths.subList(1, paths.size()));
        final BsonValue subBson = doc.get(topLevelKey);
        if (subBson == null) {
            return null;
        } else if (remainingPath.isEmpty()) {
            @SuppressWarnings("unchecked") final T result = (T) subBson;
            return result;
        }

        return getValueByPath((Bson) subBson, remainingPath);
    }

    /**
     * Pretty-prints Bson objects.
     *
     * @param bsons Bson objects to be printed, may be {@code null}.
     * @return String representation of the Bson objects.
     */
    public static String prettyPrint(@Nullable final Collection<Bson> bsons) {
        if (bsons == null) {
            return NULL_STRING;
        }

        return "[" + bsons.stream()
                .map(BsonUtil::prettyPrint)
                .collect(Collectors.joining(",\n")) + "]";
    }

    /**
     * Pretty-prints a Bson object.
     *
     * @param bson Bson object to be printed, may be {@code null}.
     * @return String representation of the Bson.
     */
    public static String prettyPrint(@Nullable final Bson bson) {
        if (bson == null) {
            return NULL_STRING;
        }

        return toBsonDocument(bson).toJson();
    }

    /**
     * Returns the codec registry.
     *
     * @return the codec registry.
     */
    public static CodecRegistry getCodecRegistry() {
        return CODEC_REGISTRY;
    }

    private static @Nullable
    <T> T getDocumentValueOrNullAt(final Document document, final String key, final Class<T> clazz) {
        return document.get(key, clazz);
    }

}
