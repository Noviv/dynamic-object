package com.github.rschmitt.dynamicobject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

public class TaggedReaderTest {
    private static final String EDN = "{:dumb [#MyDumbClass{:version 1, :str \"str\"}]}";

    @Before
    public void setup() {
        DynamicObject.registerType(DumbClass.class, new DumbClassTranslator());
    }

    @After
    public void teardown() {
        DynamicObject.deregisterType(DumbClass.class);
    }

    @Test
    public void roundTrip() {
        DumbClassHolder holder = DynamicObject.deserialize(EDN, DumbClassHolder.class);

        String serialized = DynamicObject.serialize(holder);

        assertEquals(EDN, serialized);
        assertEquals(new DumbClass(1, "str"), holder.dumb().get(0));
    }

    @Test
    public void serializeRegisteredType() {
        DumbClass dumbClass = new DumbClass(24, "twenty-four");

        String serialized = DynamicObject.serialize(dumbClass);

        assertEquals("#MyDumbClass{:version 24, :str \"twenty-four\"}", serialized);
    }

    @Test
    public void prettyPrint() {
        DumbClassHolder holder = DynamicObject.deserialize(EDN, DumbClassHolder.class);
        String expectedFormattedString = format("{:dumb [#MyDumbClass{:version 1, :str \"str\"}]}%n");
        assertEquals(expectedFormattedString, holder.toFormattedString());
    }
}

// This is a DynamicObject that contains a regular POJO.
interface DumbClassHolder extends DynamicObject<DumbClassHolder> {
    List<DumbClass> dumb();
}

// This is a translation class that functions as an Edn reader/writer for its associated POJO.
class DumbClassTranslator implements EdnTranslator<DumbClass> {
    @Override
    public DumbClass read(Object obj) {
        DumbClassProxy proxy = DynamicObject.wrap(obj, DumbClassProxy.class);
        return new DumbClass(proxy.version(), proxy.str());
    }

    @Override
    public String write(DumbClass obj) {
        DumbClassProxy proxy = DynamicObject.newInstance(DumbClassProxy.class);
        proxy = proxy.str(obj.getStr());
        proxy = proxy.version(obj.getVersion());
        return DynamicObject.serialize(proxy);
    }

    @Override
    public String getTag() {
        return "MyDumbClass"; // This is deliberately different from the class name.
    }

    interface DumbClassProxy extends DynamicObject<DumbClassProxy> {
        long version();
        String str();

        DumbClassProxy version(long version);
        DumbClassProxy str(String str);
    }
}

// This is a POJO that has no knowledge of Edn.
class DumbClass {
    private final long version;
    private final String str;

    DumbClass(long version, String str) {
        this.version = version;
        this.str = str;
    }

    public long getVersion() {
        return version;
    }

    public String getStr() {
        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DumbClass dumbClass = (DumbClass) o;

        return version == dumbClass.version && str.equals(dumbClass.str);
    }

    @Override
    public int hashCode() {
        int result = (int) (version ^ (version >>> 32));
        result = 31 * result + str.hashCode();
        return result;
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException("I'm a useless legacy toString() method that doesn't produce Edn!");
    }
}
