package mx.ucol.marc2bf.cli;

import mx.ucol.marc2bf.util.MarcRepairService;

import java.nio.file.Path;

/** Standalone CLI for MARC UTF-16/BOM/ISO2709 repair. */
public final class RepairCli {
    private RepairCli() {}

    public static void main(String[] args) {
        try {
            Path input = args.length >= 1 ? Path.of(args[0]) : Path.of("catalogo.marc");
            Path output = args.length >= 2 ? Path.of(args[1]) : Path.of("catalogo-final.mrc");
            boolean convertCaret = true;
            for (int i = 2; i < args.length; i++) {
                if ("--no-caret".equals(args[i])) convertCaret = false;
                else throw new IllegalArgumentException("Opción desconocida: " + args[i]);
            }

            System.out.println("Reparando MARC21...");
            MarcRepairService.RepairResult result = MarcRepairService.repair(input, output, convertCaret);
            printResult(result);
        } catch (Exception e) {
            System.err.println("Falló la reparación MARC: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printResult(MarcRepairService.RepairResult result) {
        System.out.println();
        System.out.println("REPARACIÓN COMPLETADA");
        System.out.println("Codificación: " + result.encodingConversion());
        System.out.printf("Marcadores ^x convertidos: %,d%n", result.caretSubfieldsConverted());
        System.out.printf("Registros reconstruidos:  %,d%n", result.recordsRebuilt());
        System.out.printf("Registros validados:       %,d%n", result.recordsValidated());
        System.out.printf("Tamaño original:           %,d bytes%n", result.originalSize());
        System.out.printf("Tamaño final:              %,d bytes%n", result.finalSize());
        System.out.println("Salida: " + result.outputFile());
    }
}
