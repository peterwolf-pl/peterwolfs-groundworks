package com.piotrek.groundworks.api.container;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleGranularContainerTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Container accepts material up to its capacity and rejects excess")
    void testAcceptMaterialCapacity() {
        SimpleGranularContainer container = new SimpleGranularContainer(512); // 1 block capacity
        assertEquals(0, container.storedUnits());
        assertTrue(container.isEmpty());
        assertTrue(container.hasRoom());

        int accepted = container.acceptMaterial(GranularMaterialRegistry.DIRT, 256);
        assertEquals(256, accepted);
        assertEquals(256, container.storedUnits());
        assertEquals(GranularMaterialRegistry.DIRT, container.storedMaterial());

        // Fill to ceiling
        int accepted2 = container.acceptMaterial(GranularMaterialRegistry.DIRT, 400);
        assertEquals(256, accepted2, "Should accept only 256 more to hit 512");
        assertEquals(512, container.storedUnits());
        assertFalse(container.hasRoom());

        // Further additions are rejected
        int accepted3 = container.acceptMaterial(GranularMaterialRegistry.DIRT, 64);
        assertEquals(0, accepted3);
    }

    @Test
    @DisplayName("Container accepts and preserves mixed materials")
    void testMaterialMixing() {
        SimpleGranularContainer container = new SimpleGranularContainer(1024);
        assertEquals(100, container.acceptMaterial(GranularMaterialRegistry.SAND, 100));
        assertEquals(50, container.acceptMaterial(GranularMaterialRegistry.GRAVEL, 50));

        assertEquals(150, container.storedUnits());
        assertEquals(100, container.storedComposition().unitsOf(GranularMaterialRegistry.SAND));
        assertEquals(50, container.storedComposition().unitsOf(GranularMaterialRegistry.GRAVEL));
        assertEquals(GranularMaterialRegistry.SAND, container.storedMaterial(),
                "Legacy material accessor should expose the dominant component");
    }

    @Test
    @DisplayName("Legacy extraction removes only the advertised dominant material")
    void testLegacyExtractionPreservesMaterialIdentity() {
        SimpleGranularContainer container = new SimpleGranularContainer(1024);
        container.acceptMaterial(GranularMaterialRegistry.SAND, 100);
        container.acceptMaterial(GranularMaterialRegistry.GRAVEL, 50);

        assertEquals(GranularMaterialRegistry.SAND, container.storedMaterial());
        assertEquals(80, container.extractMaterial(80));
        assertEquals(20, container.storedComposition().unitsOf(GranularMaterialRegistry.SAND));
        assertEquals(50, container.storedComposition().unitsOf(GranularMaterialRegistry.GRAVEL));
        assertEquals(GranularMaterialRegistry.GRAVEL, container.storedMaterial());
    }

    @Test
    @DisplayName("Extraction drains units and resets material when empty")
    void testExtraction() {
        SimpleGranularContainer container = new SimpleGranularContainer(512);
        container.acceptMaterial(GranularMaterialRegistry.GRAVEL, 200);

        int extracted = container.extractMaterial(150);
        assertEquals(150, extracted);
        assertEquals(50, container.storedUnits());

        int extracted2 = container.extractMaterial(100);
        assertEquals(50, extracted2);
        assertEquals(0, container.storedUnits());
        assertTrue(container.isEmpty());
        assertEquals(GranularMaterial.EMPTY, container.storedMaterial());
    }
}
