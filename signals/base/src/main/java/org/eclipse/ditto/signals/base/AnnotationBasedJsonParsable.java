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
 package org.eclipse.ditto.signals.base;

 import java.lang.reflect.InvocationTargetException;
 import java.lang.reflect.Method;

 import org.eclipse.ditto.json.JsonObject;
 import org.eclipse.ditto.model.base.headers.DittoHeaders;

 public final class AnnotationBasedJsonParsable<T> implements JsonParsable<T> {

     private static final Class<?> JSON_OBJECT_PARAMETER = JsonObject.class;
     private static final Class<?> DITTO_HEADERS_PARAMETER = DittoHeaders.class;

     private final String key;
     private final String v1FallbackKey;
     private final Method parseMethod;

     public AnnotationBasedJsonParsable(final String key, final String v1FallbackKey,
             final Class<? extends T> parsedClass,
             final String parsingMethodName) {
         this.key = key;
         this.v1FallbackKey = v1FallbackKey;
         try {
             this.parseMethod =
                     parsedClass.getMethod(parsingMethodName, JSON_OBJECT_PARAMETER, DITTO_HEADERS_PARAMETER);
         } catch (final NoSuchMethodException e) {
             throw new DeserializationStrategyNotFoundError(parsedClass, e);
         }
     }

     public String getKey() {
         return key;
     }

     public String getV1FallbackKey() {
         return v1FallbackKey;
     }


     @Override
     public T parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
         try {
             return (T) parseMethod.invoke(null, jsonObject, dittoHeaders);
         } catch (final ClassCastException | IllegalAccessException e) {
             throw buildJsonTypeNotParsableException(e, dittoHeaders);
         } catch (final InvocationTargetException e) {
             final Throwable targetException = e.getTargetException();

             if (targetException instanceof RuntimeException) {
                 throw (RuntimeException) targetException;
             }

             throw buildJsonTypeNotParsableException(targetException, dittoHeaders);
         }
     }

     private JsonTypeNotParsableException buildJsonTypeNotParsableException(final Throwable cause,
             final DittoHeaders dittoHeaders) {
         throw JsonTypeNotParsableException.newBuilder(key, "TODO")
                 .dittoHeaders(dittoHeaders)
                 .cause(cause)
                 .build();
     }
 }
