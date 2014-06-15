package com.github.rschmitt.dynamicobject;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.rschmitt.dynamicobject.ClojureStuff.*;

class Conversions {
    /*
     * Convert a Java object (e.g. passed in to a builder method) into the Clojure-style representation used internally.
     * This is done according to the following rules:
     *  * DynamicObject instances will be replaced with their underlying maps, but with :type metadata attached.
     *  * Boxed and unboxed numerics, as well as BigInteger, will be losslessly converted to Long, Double, or BigInt.
     *  * Supported collection types (List, Set, Map) will have their elements converted according to these rules. This
     *    also applies to nested collections. For instance, a List<List<Integer>> will effectively be converted to a
     *    List<List<Long>>.
     */
    static Object javaToClojure(Object obj) {
        Object val = Numerics.maybeUpconvert(obj);
        if (val instanceof DynamicObject)
            return unwrapAndAnnotateDynamicObject((DynamicObject<?>) obj);
        else if (val instanceof List)
            return convertCollectionToClojureTypes((Collection<?>) val, EMPTY_VECTOR);
        else if (val instanceof Set)
            return convertCollectionToClojureTypes((Collection<?>) val, EMPTY_SET);
        else if (val instanceof Map)
            return convertMapToClojureTypes((Map<?, ?>) val);
        else
            return val;
    }

    private static Object unwrapAndAnnotateDynamicObject(DynamicObject<?> dynamicObject) {
        Object map = dynamicObject.getMap();
        map = Metadata.withTypeMetadata(map, dynamicObject.getType());
        return map;
    }

    private static Object convertCollectionToClojureTypes(Collection<?> val, Object empty) {
        Object ret = TRANSIENT.invoke(empty);
        val.forEach(o -> CONJ_BANG.invoke(ret, javaToClojure(o)));
        return PERSISTENT.invoke(ret);
    }

    private static Object convertMapToClojureTypes(Map<?, ?> map) {
        Object ret = TRANSIENT.invoke(EMPTY_MAP);
        map.forEach((k, v) -> ASSOC_BANG.invoke(ret, javaToClojure(k), javaToClojure(v)));
        return PERSISTENT.invoke(ret);
    }

    /*
     * Convert a Clojure object (i.e. a value somewhere in a DynamicObject's map) into the expected Java representation.
     * This representation is determined by the generic return type of the method. The conversion is performed as
     * follows:
     *  * If the return type is a numeric type, the Clojure numeric will be downconverted to the expected type (e.g.
     *    Long -> Integer).
     *  * If the return type is a nested DynamicObject, we wrap it as the expected DynamicObject type.
     *  * If the return type is a collection type, there are a few possibilities:
     *    * If it is a raw type, no action is taken.
     *    * If it is a wildcard type (e.g. List<?>), an UnsupportedOperationException is thrown.
     *    * If the type variable is a Class, the elements of the collection are enumerated over to convert numerics and
     *      wraps DynamicObjects.
     *    * If the type variable is another collection type, the algorithm recurses.
     */
    @SuppressWarnings("unchecked")
    static Object clojureToJava(Object obj, Type genericReturnType) {
        if (genericReturnType instanceof Class) {
            Class<?> returnType = (Class<?>) genericReturnType;
            if (Numerics.isNumeric(returnType))
                return Numerics.maybeDownconvert(returnType, obj);
            if (DynamicObject.class.isAssignableFrom(returnType))
                return DynamicObject.wrap(obj, (Class<? extends DynamicObject>) returnType);
        }

        if (obj instanceof List)
            return convertCollectionToJavaTypes((Collection<?>) obj, EMPTY_VECTOR, genericReturnType);
        else if (obj instanceof Set)
            return convertCollectionToJavaTypes((Collection<?>) obj, EMPTY_SET, genericReturnType);
        else if (obj instanceof Map)
            return convertMapToJavaTypes((Map<?, ?>) obj, genericReturnType);
        else
            return obj;
    }

    private static Object convertCollectionToJavaTypes(Collection<?> coll, Object empty, Type genericReturnType) {
        Object ret = TRANSIENT.invoke(empty);
        coll.forEach(o -> CONJ_BANG.invoke(ret, convertCollectionElementToJavaTypes(o, genericReturnType)));
        return PERSISTENT.invoke(ret);
    }

    private static Object convertCollectionElementToJavaTypes(Object element, Type genericCollectionType) {
        if (genericCollectionType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericCollectionType;
            List<Type> typeArgs = Arrays.asList(parameterizedType.getActualTypeArguments());
            assert typeArgs.size() == 1;
            return clojureToJava(element, typeArgs.get(0));
        } else
            throw new UnsupportedOperationException();
    }

    private static Object convertMapToJavaTypes(Map<?, ?> unwrappedMap, Type genericReturnType) {
        Type[] actualTypeArguments = ((ParameterizedType) genericReturnType).getActualTypeArguments();
        assert actualTypeArguments.length == 2;
        Type keyType = actualTypeArguments[0];
        Type valType = actualTypeArguments[1];
        Object ret = TRANSIENT.invoke(EMPTY_MAP);
        unwrappedMap.forEach((k, v) -> ASSOC_BANG.invoke(ret, clojureToJava(k, keyType), clojureToJava(v, valType)));
        return PERSISTENT.invoke(ret);
    }
}