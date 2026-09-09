package mx.ucol.marc2bf.cli;

import mx.ucol.marc2bf.bibframe.MappingReport;
import mx.ucol.marc2bf.bibframe.MarcToBibframeConverter;
import mx.ucol.marc2bf.util.MarcRepairService;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.Record;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Command-line MARC21 to BIBFRAME converter. */
public final class ConverterCli {
    private static final String DEFAULT_BASE = "https://bibliotecas.ucol.mx/bibframe/";

    private ConverterCli() {}

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            execute(options);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Falló la conversión: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    public static void execute(Options options) throws IOException {
        Path input = options.input().toAbsolutePath().normalize();
        Path output = options.output().toAbsolutePath().normalize();
        validateInput(input);
        if (input.equals(output)) throw new IllegalArgumentException("Entrada y salida no pueden ser el mismo archivo.");
        if (output.getParent() != null) Files.createDirectories(output.getParent());

        Path marcInput = input;
        Path repairedTemp = null;
        MarcRepairService.RepairResult repairResult = null;

        try {
            if (options.repair()) {
                System.out.println("Reparación MARC activada (--repair).");
                repairedTemp = Files.createTempFile("marc-reparado-", ".mrc");
                repairResult = MarcRepairService.repair(input, repairedTemp);
                marcInput = repairedTemp;

                System.out.println("MARC reparado y validado antes de la conversión.");
                System.out.println("Codificación: " + repairResult.encodingConversion());
                System.out.printf("Marcadores ^x convertidos: %,d%n", repairResult.caretSubfieldsConverted());
                System.out.printf("Registros reparados: %,d%n", repairResult.recordsRebuilt());
                System.out.println();
            }

            MappingReport report = new MappingReport();
            MarcToBibframeConverter converter = new MarcToBibframeConverter(options.baseUri(), report);
            Instant start = Instant.now();
            int count = 0;

            Model catalog = ModelFactory.createDefaultModel();
            try {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(marcInput))) {
                    MarcReader reader = new MarcStreamReader(in);
                    while (reader.hasNext() && (options.limit() <= 0 || count < options.limit())) {
                        Record record = reader.next();
                        Model converted = converter.convert(record);
                        try {
                            catalog.add(converted);
                        } finally {
                            converted.close();
                        }
                        count++;
                        if (count % 100 == 0) System.out.printf("Procesados: %,d registros%n", count);
                    }
                }

                report.setTriples(catalog.size());
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(
                        output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
                    RDFDataMgr.write(out, catalog, options.lang());
                }
            } finally {
                catalog.close();
            }

            if (options.report() != null) report.writeCsv(options.report());
            if (options.validate()) validateRdf(output, options.lang());

            Duration duration = Duration.between(start, Instant.now());
            System.out.println();
            System.out.println("Conversión MARC21 → BIBFRAME completada.");
            System.out.println("Entrada:  " + input);
            System.out.println("Repair:   " + (options.repair() ? "sí" : "no"));
            if (repairResult != null) {
                System.out.printf("Reparados: %,d registros%n", repairResult.recordsRebuilt());
            }
            System.out.println("Salida:   " + output);
            System.out.printf("Registros: %,d%n", count);
            System.out.printf("Triples:   %,d%n", report.triples());
            System.out.printf("Tamaño:    %,d bytes%n", Files.size(output));
            System.out.printf("Tiempo:    %.2f segundos%n", duration.toMillis() / 1000.0);
            if (options.report() != null) {
                System.out.println("Reporte:   " + options.report().toAbsolutePath().normalize());
            }
        } finally {
            if (repairedTemp != null) {
                try {
                    Files.deleteIfExists(repairedTemp);
                } catch (IOException e) {
                    System.err.println("Advertencia: no se pudo eliminar el MARC temporal: " + repairedTemp);
                }
            }
        }
    }

    private static void validateRdf(Path output, Lang lang) {
        Model check = ModelFactory.createDefaultModel();
        try (InputStream in = Files.newInputStream(output)) {
            RDFDataMgr.read(check, in, null, lang);
            System.out.printf("Validación RDF: correcta (%,d triples releídas).%n", check.size());
        } catch (Exception e) {
            throw new IllegalStateException("La salida RDF no pudo releerse: " + e.getMessage(), e);
        } finally {
            check.close();
        }
    }

    private static void validateInput(Path input) {
        if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
            throw new IllegalArgumentException("No se puede leer el archivo MARC21: " + input);
        }
    }

    public static void printUsage() {
        System.err.println("""
                Uso:
                  mvn compile -Dexec.args="entrada.mrc salida.ttl [opciones]" exec:java@convertir

                Opciones:
                  --base URI               URI base de los recursos generados.
                  --format turtle|jsonld|rdfxml|ntriples
                  --report archivo.csv     Reporte de etiquetas encontradas/mapeadas.
                  --limit N                Convierte solo los primeros N registros.
                  --validate               Relee la salida para validar su sintaxis RDF.
                  --repair                 Repara UTF-16/BOM/^subcampos/Directory antes de convertir.

                Ejemplo normal:
                  mvn compile -Dexec.args="catalogo.mrc salida/catalogo.ttl --format turtle --report salida/reporte.csv --validate" exec:java@convertir

                Ejemplo con reparación automática:
                  mvn compile -Dexec.args="catalogo.marc salida/catalogo.ttl --repair --format turtle --report salida/reporte.csv --validate" exec:java@convertir
                """);
    }

    public record Options(
            Path input,
            Path output,
            String baseUri,
            Lang lang,
            Path report,
            int limit,
            boolean validate,
            boolean repair
    ) {
        public static Options parse(String[] args) {
            if (args == null || args.length < 2) throw new IllegalArgumentException("Faltan entrada.mrc y salida RDF.");
            Path input = Path.of(args[0]);
            Path output = Path.of(args[1]);
            String base = DEFAULT_BASE;
            Lang lang = inferLang(output);
            Path report = null;
            int limit = 0;
            boolean validate = false;
            boolean repair = false;

            for (int i = 2; i < args.length; i++) {
                switch (args[i]) {
                    case "--base" -> base = requiredValue(args, ++i, "--base");
                    case "--format" -> lang = parseLang(requiredValue(args, ++i, "--format"));
                    case "--report" -> report = Path.of(requiredValue(args, ++i, "--report"));
                    case "--limit" -> {
                        try {
                            limit = Integer.parseInt(requiredValue(args, ++i, "--limit"));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("--limit debe ser entero.");
                        }
                        if (limit < 0) throw new IllegalArgumentException("--limit no puede ser negativo.");
                    }
                    case "--validate" -> validate = true;
                    case "--repair" -> repair = true;
                    default -> throw new IllegalArgumentException("Opción desconocida: " + args[i]);
                }
            }
            return new Options(input, output, base, lang, report, limit, validate, repair);
        }

        private static String requiredValue(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException("Falta valor para " + option);
            return args[index];
        }

        private static Lang inferLang(Path output) {
            String name = output.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".jsonld") || name.endsWith(".json")) return Lang.JSONLD;
            if (name.endsWith(".rdf") || name.endsWith(".xml")) return Lang.RDFXML;
            if (name.endsWith(".nt")) return Lang.NTRIPLES;
            return Lang.TURTLE;
        }

        private static Lang parseLang(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "ttl", "turtle" -> Lang.TURTLE;
                case "json", "jsonld", "json-ld" -> Lang.JSONLD;
                case "rdf", "rdfxml", "rdf/xml" -> Lang.RDFXML;
                case "nt", "ntriples", "n-triples" -> Lang.NTRIPLES;
                default -> throw new IllegalArgumentException("Formato RDF no reconocido: " + value);
            };
        }
    }
}
