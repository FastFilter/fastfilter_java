package org.fastfilter.xor;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SerializationTest {

    @Test
    public void shouldSerializeAndDeserializeEmptyFilter() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L, 4L, 5L};
        final var originalFilter = XorBinaryFuse16.construct(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(buffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present in deserialized filter",
                    deserializedFilter.mayContain(key));
        }
    }

    @Test
    public void shouldSerializeAndDeserializeSmallFilter() {
        // Arrange
        final var keys = new long[]{100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L};
        final var originalFilter = XorBinaryFuse16.construct(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(buffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present in deserialized filter",
                    deserializedFilter.mayContain(key));
        }
        assertFalse("Key 50L should not be in filter", deserializedFilter.mayContain(50L));
        assertFalse("Key 1500L should not be in filter", deserializedFilter.mayContain(1500L));
    }

    @Test
    public void shouldSerializeAndDeserializeLargeFilter() {
        // Arrange
        final int size = 10000;
        final var keys = new long[size];
        for (int i = 0; i < size; i++) {
            keys[i] = i * 100L;
        }
        final var originalFilter = XorBinaryFuse16.construct(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(buffer);

        // Assert
        for (int i = 0; i < size; i++) {
            final long key = i * 100L;
            assertTrue("Key " + key + " should be present in deserialized filter",
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
        final var originalFilter = XorBinaryFuse16.construct(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(buffer);

        // Assert
        assertEquals("Bit count should be preserved", 
                originalFilter.getBitCount(), deserializedFilter.getBitCount());
        assertEquals("Serialized size should be preserved",
                originalFilter.getSerializedSize(), deserializedFilter.getSerializedSize());
        assertEquals("String representation should match",
                originalFilter.toString(), deserializedFilter.toString());
    }

    @Test
    public void shouldHandleMultipleSerializationRounds() {
        // Arrange
        final var keys = new long[]{10L, 20L, 30L, 40L, 50L};
        final var originalFilter = XorBinaryFuse16.construct(keys);
        final var buffer1 = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act - First round
        originalFilter.serialize(buffer1);
        buffer1.flip();
        final var filter1 = XorBinaryFuse16.deserialize(buffer1);

        // Act - Second round
        final var buffer2 = ByteBuffer.allocate(filter1.getSerializedSize());
        filter1.serialize(buffer2);
        buffer2.flip();
        final var filter2 = XorBinaryFuse16.deserialize(buffer2);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present after first deserialization",
                    filter1.mayContain(key));
            assertTrue("Key " + key + " should be present after second deserialization",
                    filter2.mayContain(key));
        }
    }

    @Test
    public void shouldThrowExceptionWhenSerializeBufferTooSmall() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L, 4L, 5L};
        final var filter = XorBinaryFuse16.construct(keys);
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
            XorBinaryFuse16.deserialize(tooSmallBuffer);
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
        final var originalFilter = XorBinaryFuse16.construct(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(buffer);

        // Assert
        for (int i = 0; i < size; i++) {
            assertTrue("Sequential key " + i + " should be present",
                    deserializedFilter.mayContain(i));
        }
        assertFalse("Key outside range should not be in filter",
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
        final var originalFilter = XorBinaryFuse16.construct(keys);
        final var buffer = ByteBuffer.allocate(originalFilter.getSerializedSize());

        // Act
        originalFilter.serialize(buffer);
        buffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(buffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Large key " + key + " should be present",
                    deserializedFilter.mayContain(key));
        }
    }

    @Test
    public void shouldCorrectlyCalculateSerializedSize() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};
        final var filter = XorBinaryFuse16.construct(keys);
        final int expectedSizeInBytes = filter.getSerializedSize();
        final var buffer = ByteBuffer.allocate(expectedSizeInBytes);

        // Act
        filter.serialize(buffer);

        // Assert
        assertEquals("Buffer position should equal serialized size",
                expectedSizeInBytes, buffer.position());
        assertEquals("Buffer should have no remaining space",
                0, buffer.remaining());
    }

    @Test
    public void shouldHandleExactBufferSize() {
        // Arrange
        final var keys = new long[]{100L, 200L, 300L};
        final var filter = XorBinaryFuse16.construct(keys);
        final var exactBuffer = ByteBuffer.allocate(filter.getSerializedSize());

        // Act
        filter.serialize(exactBuffer);
        exactBuffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(exactBuffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present with exact buffer",
                    deserializedFilter.mayContain(key));
        }
        assertEquals("No bytes should remain in buffer", 0, exactBuffer.remaining());
    }

    @Test
    public void shouldHandleLargerBufferThanNeeded() {
        // Arrange
        final var keys = new long[]{1L, 2L, 3L};
        final var filter = XorBinaryFuse16.construct(keys);
        final var largeBuffer = ByteBuffer.allocate(filter.getSerializedSize() + 1000);

        // Act
        filter.serialize(largeBuffer);
        largeBuffer.flip();
        final var deserializedFilter = XorBinaryFuse16.deserialize(largeBuffer);

        // Assert
        for (final long key : keys) {
            assertTrue("Key " + key + " should be present with larger buffer",
                    deserializedFilter.mayContain(key));
        }
    }
}
