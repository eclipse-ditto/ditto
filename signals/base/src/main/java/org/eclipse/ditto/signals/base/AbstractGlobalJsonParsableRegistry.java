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

 import java.lang.annotation.Annotation;
 import java.util.HashMap;
 import java.util.Map;
 import java.util.stream.StreamSupport;

 import org.atteo.classindex.ClassIndex;

 public abstract class AbstractGlobalJsonParsableRegistry<T, A extends Annotation>
         extends AbstractJsonParsableRegistry<T> {

     protected AbstractGlobalJsonParsableRegistry(
             final Class<T> parsedClass,
             final Class<A> annotationClass,
             final AbstractAnnotationBasedJsonParsableBuilder<T, A> annotationBasedJsonParsableBuilder) {

         super(initAnnotationBasedParseStrategies(parsedClass, annotationClass, annotationBasedJsonParsableBuilder));
     }

     protected AbstractGlobalJsonParsableRegistry(
             final Class<T> parsedClass,
             final Class<A> annotationClass,
             final AbstractAnnotationBasedJsonParsableBuilder<T, A> annotationBasedJsonParsableBuilder,
             final Map<String, JsonParsable<T>> parseStrategies) {

         super(mergeParsingStrategies(
                 initAnnotationBasedParseStrategies(parsedClass, annotationClass, annotationBasedJsonParsableBuilder),
                 parseStrategies)
         );
     }

     private static <T> Map<String, JsonParsable<T>> mergeParsingStrategies(
             final Map<String, JsonParsable<T>> annotationBasedStrategies,
             final Map<String, JsonParsable<T>> otherStrategies) {

         final HashMap<String, JsonParsable<T>> mergedStrategies = new HashMap<>();
         mergedStrategies.putAll(annotationBasedStrategies);
         mergedStrategies.putAll(otherStrategies);
         return mergedStrategies;
     }

     private static <T, A extends Annotation> Map<String, JsonParsable<T>> initAnnotationBasedParseStrategies(
             final Class<T> baseClass,
             final Class<A> annotationClass,
             final AbstractAnnotationBasedJsonParsableBuilder<T, A> annotationBasedJsonParsableBuilder) {

         final Map<String, JsonParsable<T>> parseRegistries = new HashMap<>();
         final Iterable<Class<?>> annotatedClasses = ClassIndex.getAnnotated(annotationClass);

         StreamSupport.stream(annotatedClasses.spliterator(), false)
                 .filter(baseClass::isAssignableFrom)
                 .map(cls -> (Class<? extends T>) cls)
                 .forEach(classToParse -> {
                     final A fromJsonAnnotation = classToParse.getAnnotation(annotationClass);

                     final AnnotationBasedJsonParsable<T> strategy =
                             annotationBasedJsonParsableBuilder.fromAnnotation(fromJsonAnnotation, classToParse);
                     parseRegistries.put(strategy.getKey(), strategy);
                     parseRegistries.put(strategy.getV1FallbackKey(), strategy);
                 });

         return parseRegistries;
     }

 }
