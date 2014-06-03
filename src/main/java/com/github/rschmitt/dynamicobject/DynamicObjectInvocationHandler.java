package com.github.rschmitt.dynamicobject;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

class DynamicObjectInvocationHandler<T extends DynamicObject<T>> implements InvocationHandler {
    private static final IFn PPRINT;

    static {
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("clojure.pprint"));

        PPRINT = Clojure.var("clojure.pprint/pprint");
    }

    private final IPersistentMap map;
    private final Class<T> type;
    private final Constructor<MethodHandles.Lookup> lookupConstructor;

    DynamicObjectInvocationHandler(IPersistentMap map, Class<T> type, Constructor<MethodHandles.Lookup> lookupConstructor) {
        this.map = map;
        this.type = type;
        this.lookupConstructor = lookupConstructor;
    }

    private T assoc(String key, Object value) {
        Keyword keyword = (Keyword) Clojure.read(":" + key);
        if (value instanceof DynamicObject)
            value = ((DynamicObject) value).getMap();
        IPersistentMap newMap = map.assoc(keyword, value);
        return DynamicObject.wrap(newMap, type);
    }

    private T assocEx(String key, Object value) {
        Keyword keyword = (Keyword) Clojure.read(":" + key);
        if (value instanceof DynamicObject)
            value = ((DynamicObject) value).getMap();
        IPersistentMap newMap = map.assocEx(keyword, value);
        return DynamicObject.wrap(newMap, type);
    }

    private T without(String key) {
        Keyword keyword = (Keyword) Clojure.read(":" + key);
        return DynamicObject.wrap(map.without(keyword), type);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if (method.getReturnType().equals(type) && (args != null && args.length > 0))
            return assoc(methodName, args[0]);

        if (method.isDefault())
            return invokeDefaultMethod(proxy, method, args);

        switch (methodName) {
            case "getMap":
                return map;
            case "getType":
                return type;
            case "assoc":
                return assoc((String) args[0], args[1]);
            case "assocEx":
                return assocEx((String) args[0], args[1]);
            case "dissoc":
                return without((String) args[0]);
            case "toString":
                return map.toString();
            case "hashCode":
                return map.hashCode();
            case "prettyPrint":
                PPRINT.invoke(map);
                return null;
            case "toFormattedString":
                Writer w = new StringWriter();
                PPRINT.invoke(map, w);
                return w.toString();
            case "equals":
                Object other = args[0];
                if (other instanceof DynamicObject)
                    return map.equals(((DynamicObject) other).getMap());
                else
                    return method.invoke(map, args);
            default:
                return getValueFor(method);
        }
    }

    private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        int TRUSTED = -1;
        return lookupConstructor.newInstance(declaringClass, TRUSTED)
                .unreflectSpecial(method, declaringClass)
                .bindTo(proxy)
                .invokeWithArguments(args);
    }

    @SuppressWarnings("unchecked")
    private Object getValueFor(Method method) {
        String methodName = method.getName();
        Keyword keywordKey = (Keyword) Clojure.read(":" + methodName);
        IMapEntry entry = map.entryAt(keywordKey);
        if (entry == null)
            entry = getNonDefaultKey(method);
        if (entry == null)
            return null;
        Object val = entry.val();
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(int.class) || returnType.equals(Integer.class))
            return returnInt(val);
        if (returnType.equals(float.class) || returnType.equals(Float.class))
            return returnFloat(val);
        if (returnType.equals(short.class) || returnType.equals(Short.class))
            return returnShort(val);
        if (DynamicObject.class.isAssignableFrom(returnType)) {
            Class<T> dynamicObjectType = (Class<T>) returnType;
            return DynamicObject.wrap((IPersistentMap) map.valAt(Clojure.read(":" + methodName)), dynamicObjectType);
        }
        return val;
    }

    private float returnFloat(Object val) {
        if (val instanceof Float)
            return (Float) val;
        return ((Double) val).floatValue();
    }

    private int returnInt(Object val) {
        if (val instanceof Integer)
            return (Integer) val;
        else return ((Long) val).intValue();
    }

    private short returnShort(Object val) {
        if (val instanceof Short)
            return (Short) val;
        if (val instanceof Integer)
            return ((Integer) val).shortValue();
        else return ((Long) val).shortValue();
    }

    private IMapEntry getNonDefaultKey(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().equals(Key.class)) {
                String key = ((Key) annotation).value();
                if (key.charAt(0) != ':')
                    key = ":" + key;
                return map.entryAt(Clojure.read(key));
            }
        }
        return null;
    }
}
