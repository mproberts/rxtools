package com.github.mproberts.rxtools.types;

import io.reactivex.functions.Function;
import org.junit.Test;

import static org.junit.Assert.*;

public class OptionalTest
{
    @Test
    public void testPresentValue()
    {
        Optional<String> emptyOptional = Optional.ofNullable(null);
        Optional<String> fullOptional = Optional.of("test");
        Optional<String> altFullOptional = Optional.ofNullable("test2");

        assertFalse(Optional.empty().isPresent());
        assertFalse(emptyOptional.isPresent());

        assertTrue(fullOptional.isPresent());
        assertTrue(altFullOptional.isPresent());
    }

    @Test
    public void testOrElse()
    {
        Optional<String> emptyOptional = Optional.ofNullable(null);
        Optional<String> stringOptional = Optional.of("test");

        assertEquals("foo", emptyOptional.orElse("foo"));
        assertEquals("test", stringOptional.orElse("foo"));
    }

    @Test
    public void testMap()
    {
        Optional<Integer> intOptional = Optional.of(2);

        assertEquals("6", intOptional.map(new Function<Integer, String>() {
            @Override
            public String apply(Integer integer) throws Exception {
                return integer * 3 + "";
            }
        }).get());
    }

    @Test(expected = RuntimeException.class)
    public void testMapException()
    {
        Optional<Integer> intOptional = Optional.of(2);

        assertEquals("6", intOptional.map(new Function<Integer, String>() {
            @Override
            public String apply(Integer integer) throws Exception {
                throw new IllegalStateException("Well that was expected");
            }
        }));
    }
}
