package com.ikalagaming.converter.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.asset.AssetValidator;
import com.ikalagaming.graphics.asset.KnownSections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class AssetSamplesTest {

    @TempDir Path folder;

    @BeforeEach
    void setUp() throws IOException {
        assertEquals(5, AssetSamples.write(folder).size());
    }

    private AssetValidator.Inspection inspect(String name) throws IOException {
        byte[] bytes = Files.readAllBytes(folder.resolve(name));
        return AssetValidator.inspect(ByteBuffer.wrap(bytes), KnownSections.graphics());
    }

    private static List<String> errors(AssetValidator.Inspection inspection) {
        return inspection.problems().stream()
                .filter(p -> p.severity() == AssetValidator.Severity.ERROR)
                .map(AssetValidator.Problem::message)
                .toList();
    }

    @Test
    void validSampleHasNoProblems() throws IOException {
        AssetValidator.Inspection inspection = inspect(AssetSamples.VALID);

        assertTrue(inspection.problems().isEmpty(), inspection.problems().toString());
        assertEquals(3, inspection.entries().size(), "META, DEMO and NOTE");
        assertEquals("Asset-Converter", inspection.metadata().exporter());
    }

    @Test
    void unknownOptionalSectionsAreSkipped() throws IOException {
        AssetValidator.Inspection inspection = inspect(AssetSamples.UNKNOWN_OPTIONAL);

        assertTrue(inspection.isReadable());
        assertFalse(inspection.entries().getLast().known());
    }

    @Test
    void unknownRequiredSectionsAreRejected() throws IOException {
        AssetValidator.Inspection inspection = inspect(AssetSamples.UNKNOWN_REQUIRED);

        List<String> errors = errors(inspection);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains("FUTR"));
    }

    @Test
    void badChecksumIsOnTheLastSection() throws IOException {
        AssetValidator.Inspection inspection = inspect(AssetSamples.BAD_CHECKSUM);

        List<String> errors = errors(inspection);
        assertEquals(1, errors.size(), errors.toString());
        assertFalse(inspection.entries().getLast().crcMatches());
        assertNotNull(inspection.entries().getLast().section());
    }

    @Test
    void truncatedSampleLosesItsLastSection() throws IOException {
        AssetValidator.Inspection inspection = inspect(AssetSamples.TRUNCATED);

        List<String> errors = errors(inspection);
        assertEquals(2, errors.size(), "The length and the last section: " + errors);
        assertNull(inspection.entries().getLast().section());
        assertNotNull(inspection.metadata(), "Everything before the cut still reads");
    }
}
