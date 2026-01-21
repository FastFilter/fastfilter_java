package org.fastfilter.xor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;
import org.fastfilter.Filter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SerializationTest {

    private final String filterName;
    private final Function<long[], Filter> constructor;
    private final Function<ByteBuffer, Filter> deserializer;
    private final StreamDeserializer streamDeserializer;

    public SerializationTest(String filterName,
        Function<long[], Filter> constructor,
        Function<ByteBuffer, Filter> deserializer,
        StreamDeserializer streamDeserializer) {
        this.filterName = filterName;
        this.constructor = constructor;
        this.deserializer = deserializer;
        this.streamDeserializer = streamDeserializer;
    }

    @Parameters(name = "{0}")
    public static List<Object[]> filters() {
        return List.of(
            new Object[] {"Xor8", (Function<long[], Filter>) Xor8::construct,
                (Function<ByteBuffer, Filter>) Xor8::deserialize,
                (StreamDeserializer) Xor8::deserialize},
            new Object[] {"Xor16", (Function<long[], Filter>) Xor16::construct,
                (Function<ByteBuffer, Filter>) Xor16::deserialize,
                (StreamDeserializer) Xor16::deserialize},
            new Object[] {"XorBinaryFuse8", (Function<long[], Filter>) XorBinaryFuse8::construct,
                (Function<ByteBuffer, Filter>) XorBinaryFuse8::deserialize,
                (StreamDeserializer) XorBinaryFuse8::deserialize},
            new Object[] {"XorBinaryFuse16", (Function<long[], Filter>) XorBinaryFuse16::construct,
                (Function<ByteBuffer, Filter>) XorBinaryFuse16::deserialize,
                (StreamDeserializer) XorBinaryFuse16::deserialize},
            new Object[] {"XorBinaryFuse32", (Function<long[], Filter>) XorBinaryFuse32::construct,
                (Function<ByteBuffer, Filter>) XorBinaryFuse32::deserialize,
                (StreamDeserializer) XorBinaryFuse32::deserialize}
        );
    }

    @Test
    public void shouldSerializeAndDeserializeSmallFilter() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L, 4L, 5L};
        final var originalFilter = constructor.apply(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = deserializer.apply(buffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present in deserialized " + filterName + " filter",
                    deserializedFilter.mayContain(key));
        }
    }

    @Test
    public void shouldSerializeAndDeserializeMediumFilter() {
        // Arrange
        final var keys = new long[]{100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L};
        final var originalFilter = constructor.apply(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = deserializer.apply(buffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present in deserialized " + filterName + " filter",
                    deserializedFilter.mayContain(key));
        }
        assertFalse("Key 50L should not be in " + filterName + " filter", deserializedFilter.mayContain(50L));
        assertFalse("Key 1500L should not be in " + filterName + " filter", deserializedFilter.mayContain(1500L));
    }

    @Test
    public void shouldSerializeAndDeserializeMediumFilterFromStream() throws IOException {
        // Arrange
        final var keys = new long[]{100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L};
        final var originalFilter = constructor.apply(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        final var input = new ByteArrayInputStream(buffer.array());
        final var deserializedFilter = streamDeserializer.deserialize(input);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present in deserialized " + filterName + " filter",
                    deserializedFilter.mayContain(key));
        }
        assertFalse("Key 50L should not be in " + filterName + " filter", deserializedFilter.mayContain(50L));
        assertFalse("Key 1500L should not be in " + filterName + " filter", deserializedFilter.mayContain(1500L));
    }

    @Test
    public void shouldSerializeToStreamAndDeserializeFromByteBuffer() throws IOException {
        // Arrange
        final var keys = new long[]{10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L};
        final var originalFilter = constructor.apply(keys);
        final var out = new ByteArrayOutputStream();

        // Act
        originalFilter.serialize(out);
        final var buffer = ByteBuffer.wrap(out.toByteArray());
        final var deserializedFilter = deserializer.apply(buffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present in deserialized " + filterName + " filter",
                    deserializedFilter.mayContain(key));
        }
        assertFalse("Key 15L should not be in " + filterName + " filter", deserializedFilter.mayContain(15L));
    }

    @FunctionalInterface
    private interface StreamDeserializer {
        Filter deserialize(InputStream in) throws IOException;
    }

    @Test
    public void shouldSerializeAndDeserializeLargeFilter() {
        // Arrange
        final int size = 10000;
        final var keys = new long[size];
        for (int i = 0; i < size; i++) {
            keys[i] = i * 100L;
        }
        final var originalFilter = constructor.apply(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = deserializer.apply(buffer);

        // Assert
        for (int i = 0; i < size; i++) {
            final long key = i * 100L;
            assertTrue("Key " + key + " should be present in deserialized " + filterName + " filter",
                    deserializedFilter.mayContain(key));
        }
        // Test some keys that should not be in the filter
        assertFalse("Key 1L should not be in filter", deserializedFilter.mayContain(1L));
        assertFalse("Key 50L should not be in filter", deserializedFilter.mayContain(50L));
        assertFalse("Key 99L should not be in filter", deserializedFilter.mayContain(99L));
    }

    @Test
    public void shouldPreserveFilterCharacteristicsAfterDeserialization() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};
        final var originalFilter = constructor.apply(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = deserializer.apply(buffer);

        // Assert
        assertEquals("Bit count should be preserved for " + filterName,
                originalFilter.getBitCount(), deserializedFilter.getBitCount());
        assertEquals("Serialized size should be preserved for " + filterName,
                originalFilter.getSerializedSize(), deserializedFilter.getSerializedSize());
    }

    @Test
    public void shouldHandleMultipleSerializationRounds() {
        // Arrange
        final var keys = new long[]{10L, 20L, 30L, 40L, 50L};
        final var originalFilter = constructor.apply(keys);
        final var buffer1 = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act - First round
        originalFilter.serialize(buffer1);
        buffer1.flip();
        final var filter1 = deserializer.apply(buffer1);

        // Act - Second round
        final var buffer2 = ByteBuffer.allocate(filter1.getSerializedSize());
        filter1.serialize(buffer2);
        buffer2.flip();
        final var filter2 = deserializer.apply(buffer2);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present after first deserialization of " + filterName,
                    filter1.mayContain(key));
            assertTrue("Key " + key + " should be present after second deserialization of " + filterName,
                    filter2.mayContain(key));
        }
    }

    @Test
    public void shouldThrowExceptionWhenSerializeBufferTooSmall() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L, 4L, 5L};
        final var filter = constructor.apply(keys);
        final var smallBuffer = ByteBuffer.allocate(filter.getSerializedSize() - 1);

        // Act & Assert
        try {
            filter.serialize(smallBuffer);
            fail("Should have thrown IllegalArgumentException for buffer too small");
        } catch (IllegalArgumentException e) {
            assertEquals("Buffer too small", e.getMessage());
        }
    }

    @Test
    public void shouldThrowExceptionWhenDeserializeBufferTooSmall() {
        // Arrange
        final var tooSmallBuffer = ByteBuffer.allocate(10);

        // Act & Assert
        try {
            deserializer.apply(tooSmallBuffer);
            fail("Should have thrown IllegalArgumentException for buffer too small");
        } catch (IllegalArgumentException e) {
            assertEquals("Buffer too small", e.getMessage());
        }
    }

    @Test
    public void shouldHandleFilterWithSequentialKeys() {
        // Arrange
        final int size = 1000;
        final var keys = new long[size];
        for (int i = 0; i < size; i++) {
            keys[i] = i;
        }
        final var originalFilter = constructor.apply(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = deserializer.apply(buffer);

        // Assert
        for (int i = 0; i < size; i++) {
            assertTrue("Sequential key " + i + " should be present in " + filterName,
                    deserializedFilter.mayContain(i));
        }
        assertFalse("Key outside range should not be in " + filterName + " filter",
                deserializedFilter.mayContain(size + 1000));
    }

    @Test
    public void shouldHandleFilterWithRandomLargeKeys() {
        // Arrange
        final var keys = new long[]{
                Long.MAX_VALUE - 1,
                Long.MAX_VALUE - 100,
                Long.MAX_VALUE - 1000,
                Long.MAX_VALUE / 2,
                Long.MAX_VALUE / 3
        };
        final var originalFilter = constructor.apply(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = deserializer.apply(buffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Large key " + key + " should be present in " + filterName,
                    deserializedFilter.mayContain(key));
        }
    }

    @Test
    public void shouldCorrectlyCalculateSerializedSize() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};
        final var filter = constructor.apply(keys);
        final int expectedSizeInBytes = filter.getSerializedSize();
        final var buffer = ByteBuffer.allocate(expectedSizeInBytes);

        // Act
        filter.serialize(buffer);

        // Assert
        assertEquals("Buffer position should equal serialized size for " + filterName,
                expectedSizeInBytes, buffer.position());
        assertEquals("Buffer should have no remaining space for " + filterName,
                0, buffer.remaining());
    }

    @Test
    public void shouldHandleExactBufferSize() {
        // Arrange
        final var keys = new long[]{100L, 200L, 300L};
        final var filter = constructor.apply(keys);
        final var exactBuffer = ByteBuffer.allocate(filter.getSerializedSize());

        // Act
        filter.serialize(exactBuffer);
        exactBuffer.flip();
        final var deserializedFilter = deserializer.apply(exactBuffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present with exact buffer in " + filterName,
                    deserializedFilter.mayContain(key));
        }
        assertEquals("No bytes should remain in buffer for " + filterName, 0, exactBuffer.remaining());
    }

    @Test
    public void shouldHandleLargerBufferThanNeeded() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L};
        final var filter = constructor.apply(keys);
        final var largeBuffer = ByteBuffer.allocate(filter.getSerializedSize() + 1000);

        // Act
        filter.serialize(largeBuffer);
        largeBuffer.flip();
        final var deserializedFilter = deserializer.apply(largeBuffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present with larger buffer in " + filterName,
                    deserializedFilter.mayContain(key));
        }
    }
}
