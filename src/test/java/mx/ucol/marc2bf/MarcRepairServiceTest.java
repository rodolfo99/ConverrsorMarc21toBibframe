package mx.ucol.marc2bf;

import mx.ucol.marc2bf.util.MarcRepairService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MarcRepairServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void repairsUtf16CaretAndRebuildsIso2709() throws Exception {
        // 2 directory entries: 001 and 245. The 245 intentionally uses ^a instead of 0x1F a.
        String leader = "00065nam a2200049 i 4500";
        String directory = "001000400000245001100004";
        String fields = "123\u001e10^aTitulo\u001e\u001d";
        String pseudoMarc = leader + directory + "\u001e" + fields;

        Path input = tempDir.resolve("entrada.marc");
        Path output = tempDir.resolve("salida.mrc");
        Files.write(input, pseudoMarc.getBytes(StandardCharsets.UTF_16LE));

        // Add UTF-16LE BOM just like the problematic export seen in practice.
        byte[] noBom = Files.readAllBytes(input);
        byte[] withBom = new byte[noBom.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(noBom, 0, withBom, 2, noBom.length);
        Files.write(input, withBom);

        MarcRepairService.RepairResult result = MarcRepairService.repair(input, output);
        assertEquals(1, result.recordsRebuilt());
        assertEquals(1, result.recordsValidated());
        assertEquals(1, result.caretSubfieldsConverted());

        byte[] repaired = Files.readAllBytes(output);
        assertEquals('a', repaired[9]);
        assertEquals(0x1D, repaired[repaired.length - 1] & 0xFF);

        try (InputStream in = Files.newInputStream(output)) {
            MarcReader reader = new MarcStreamReader(in);
            assertTrue(reader.hasNext());
            Record record = reader.next();
            assertEquals("123", record.getControlNumber());
            DataField title = (DataField) record.getVariableField("245");
            assertNotNull(title);
            assertEquals("Titulo", title.getSubfield('a').getData());
            assertFalse(reader.hasNext());
        }
    }
}
