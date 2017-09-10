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
package org.eclipse.services.thingsearch.persistence;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import com.mongodb.async.client.MongoClientSettings;

/**
 * Utility methods for criteria creation.
 */
@Immutable
public final class BsonUtil {

    static final CodecRegistry CODEC_REGISTRY = MongoClientSettings.builder().build().getCodecRegistry();

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
                .collect(Collectors.toList());
    }

    /**
     * Returns the value to which the specified key is mapped, or {@code null} if this map contains no mapping for
     * the key. The value is cast to type {@code <T>}.
     *
     * @param document the document whose value is requested.
     * @param key the key which holds the value.
     * @param <T> the type to which the value will be cast.
     * @return the value or {@code null}.
     * @throws ClassCastException if the value has not the expected type.
     *
     * @see Document#get(Object)
     */
    public static <T> T getDocumentValueAt(final Document document, final String key) {
        @SuppressWarnings("unchecked")
        final T value = (T) document.get(key);
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
            @SuppressWarnings("unchecked")
            final T result = (T) subBson;
            return result;
        }

        return getValueByPath((Bson) subBson, remainingPath);
    }

    /**
     * Pretty-prints Bson objects.
     *
     * @param bsons Bson objects to be printed.
     * @return String representation of the Bson.
     */
    public static String prettyPrintPipeline(final Collection<Bson> bsons) {
        return "[" + bsons.stream()
                .map(bson -> BsonUtil.toBsonDocument(bson).toJson())
                .collect(Collectors.joining(",\n")) + "]";
    }

}
