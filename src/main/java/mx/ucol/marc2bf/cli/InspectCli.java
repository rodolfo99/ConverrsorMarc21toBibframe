package mx.ucol.marc2bf.cli;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.Record;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Prints MARC records in Marc4J's textual representation. */
public final class InspectCli {
    private InspectCli() {}

    public static void main(String[] args) {
        try {
            Path input = Path.of(args.length >= 1 ? args[0] : "catalogo.mrc");
            int limit = args.length >= 2 ? Integer.parseInt(args[1]) : 5;
            int count = 0;
            try (InputStream in = new BufferedInputStream(Files.newInputStream(input))) {
                MarcReader reader = new MarcStreamReader(in);
                while (reader.hasNext() && count < limit) {
                    Record record = reader.next();
                    count++;
                    System.out.println("========== Registro " + count + " ==========");
                    System.out.println(record);
                }
            }
            System.out.println("Registros mostrados: " + count);
        } catch (Exception e) {
            System.err.println("No se pudo leer el catálogo: " + e.getMessage());
            System.err.println("Uso: mvn -Dexec.args=\"catalogo.mrc 5\" exec:java@inspeccionar");
            System.exit(1);
        }
    }
}
