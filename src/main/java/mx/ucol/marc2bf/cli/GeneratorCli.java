package mx.ucol.marc2bf.cli;

import mx.ucol.marc2bf.generator.SyntheticMarcGenerator;

import java.nio.file.Files;
import java.nio.file.Path;

/** Generates an ISO2709 sample catalog. */
public final class GeneratorCli {
    private GeneratorCli() {}

    public static void main(String[] args) {
        try {
            Path output = Path.of(args.length >= 1 ? args[0] : "catalogo.mrc");
            int amount = args.length >= 2 ? Integer.parseInt(args[1]) : 1000;
            long seed = args.length >= 3 ? Long.parseLong(args[2]) : 12345L;
            new SyntheticMarcGenerator(seed).generate(output, amount);
            System.out.println("Archivo MARC21 ISO2709 generado: " + output.toAbsolutePath().normalize());
            System.out.printf("Registros: %,d%n", amount);
            System.out.printf("Tamaño: %,d bytes%n", Files.size(output));
        } catch (Exception e) {
            System.err.println("No se pudo generar el catálogo: " + e.getMessage());
            System.err.println("Uso: mvn -Dexec.args=\"catalogo.mrc 1000 12345\" exec:java@generar");
            System.exit(1);
        }
    }
}
