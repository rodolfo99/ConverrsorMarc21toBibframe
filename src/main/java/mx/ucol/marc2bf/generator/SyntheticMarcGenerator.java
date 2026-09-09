package mx.ucol.marc2bf.generator;

import org.marc4j.MarcStreamWriter;
import org.marc4j.MarcWriter;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/** Generates reproducible MARC21 ISO2709 data for testing. */
public final class SyntheticMarcGenerator {
    private static final DateTimeFormatter FIELD_005 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.S");
    private final MarcFactory factory = MarcFactory.newInstance();
    private final Random random;

    private static final List<String> AUTHORS = List.of(
            "García López, Carlos", "Valencia Rodríguez, Rodolfo", "Hernández Pérez, Ana",
            "Mendoza Torres, Jorge", "Smith, Alice", "Johnson, Robert", "Ruiz Campos, Elena");
    private static final List<String> TOPICS = List.of(
            "Java (Lenguaje de programación)", "Inteligencia artificial", "Bibliotecas digitales",
            "MARC 21", "BIBFRAME", "Historia de México", "Teología", "Matemáticas", "Literatura mexicana");
    private static final List<String> TITLES = List.of(
            "Fundamentos de Java", "Introducción a BIBFRAME", "Sistemas de bibliotecas digitales",
            "Inteligencia artificial aplicada", "Historia regional de Colima", "Estudios de teología",
            "Métodos modernos de matemáticas", "Narrativa mexicana contemporánea");
    private static final List<String> PUBLISHERS = List.of(
            "Universidad de Colima", "Fondo de Cultura Económica", "O'Reilly Media",
            "Addison-Wesley", "Springer", "Editorial Ejemplo");

    public SyntheticMarcGenerator(long seed) {
        this.random = new Random(seed);
    }

    public void generate(Path output, int amount) throws IOException {
        if (amount <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        Path absolute = output.toAbsolutePath().normalize();
        if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(
                absolute, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
            MarcWriter writer = new MarcStreamWriter(out, "UTF-8");
            try {
                for (int i = 1; i <= amount; i++) writer.write(createRecord(i));
            } finally {
                writer.close();
            }
        }
    }

    public Record createRecord(int number) {
        String id = String.format("%09d", number);
        String title = choose(TITLES) + (number % 5 == 0 ? ". Parte " + ((number % 3) + 1) : "");
        String author = choose(AUTHORS);
        String publisher = choose(PUBLISHERS);
        int year = 1980 + random.nextInt(47);
        String language = random.nextInt(100) < 78 ? "spa" : "eng";
        String dewey = deweyFor(title);
        String isbn = isbn13(number);

        Record record = factory.newRecord("00000nam a2200000 i 4500");
        record.addVariableField(factory.newControlField("001", id));
        record.addVariableField(factory.newControlField("003", "MX-COL"));
        record.addVariableField(factory.newControlField("005", LocalDateTime.of(2026, 8, 6, 12, 0).plusSeconds(number).format(FIELD_005)));
        record.addVariableField(factory.newControlField("008", field008(year, language)));

        add(record, "020", ' ', ' ', 'a', isbn, 'q', "pasta blanda");
        add(record, "035", ' ', ' ', 'a', "(MX-COL)" + id);
        add(record, "040", ' ', ' ', 'a', "MX-COL", 'b', "spa", 'e', "rda", 'c', "MX-COL");
        add(record, "041", '0', ' ', 'a', language);
        add(record, "082", '0', '4', 'a', dewey, '2', "23");
        add(record, "100", '1', ' ', 'a', author, 'e', "autor", '4', "aut");
        add(record, "245", '1', '0', 'a', title + " :", 'b', "teoría, métodos y aplicaciones /", 'c', naturalName(author) + ".");
        if (number % 4 == 0) add(record, "246", '3', ' ', 'a', "Título alternativo de " + title);
        if (number % 3 == 0) add(record, "250", ' ', ' ', 'a', ((number % 4) + 1) + "a edición.");
        add(record, "264", ' ', '1', 'a', publicationPlace(publisher) + " :", 'b', publisher + ",", 'c', year + ".");
        add(record, "300", ' ', ' ', 'a', (120 + random.nextInt(600)) + " páginas :", 'b', "ilustraciones ;", 'c', "24 cm.");
        add(record, "336", ' ', ' ', 'a', "texto", 'b', "txt", '2', "rdacontent");
        add(record, "337", ' ', ' ', 'a', "sin medio", 'b', "n", '2', "rdamedia");
        add(record, "338", ' ', ' ', 'a', "volumen", 'b', "nc", '2', "rdacarrier");
        if (number % 4 == 0) add(record, "490", '1', ' ', 'a', "Colección universitaria", 'v', "volumen " + ((number % 20) + 1));
        add(record, "500", ' ', ' ', 'a', "Registro sintético generado para pruebas de conversión.");
        if (number % 2 == 0) add(record, "504", ' ', ' ', 'a', "Incluye referencias bibliográficas e índice.");
        if (number % 3 == 0) add(record, "505", '0', ' ', 'a', "Fundamentos -- Métodos -- Aplicaciones -- Conclusiones.");
        add(record, "520", ' ', ' ', 'a', "Presenta una introducción amplia a " + title.toLowerCase() + ".");
        if (number % 6 == 0) add(record, "521", ' ', ' ', 'a', "Estudiantes universitarios y profesionales.");
        add(record, "650", ' ', '4', 'a', subjectFor(title));
        add(record, "650", ' ', '4', 'a', choose(TOPICS));
        if (number % 5 == 0) add(record, "651", ' ', '4', 'a', "Colima (México)");
        if (number % 4 == 0) add(record, "655", ' ', '7', 'a', "Manuales", '2', "tgfc");
        if (number % 2 == 0) add(record, "700", '1', ' ', 'a', choose(AUTHORS), 'e', "editor", '4', "edt");
        if (number % 7 == 0) add(record, "710", '2', ' ', 'a', "Universidad de Colima", 'e', "entidad editora", '4', "pbl");
        if (number % 8 == 0) add(record, "776", '0', '8', 'i', "Versión electrónica:", 't', title, 'z', isbn, 'w', "(MX-COL)E" + id);
        add(record, "852", ' ', ' ', 'a', "MX-COL", 'b', "Biblioteca Central", 'c', "Acervo general", 'h', dewey, 'i', author.substring(0, Math.min(3, author.length())).toUpperCase());
        add(record, "856", '4', '0', 'u', "https://catalogo.ejemplo.mx/record/" + id, 'y', "Acceso al registro");
        add(record, "876", ' ', ' ', 'a', "ITEM-" + id, 'p', "BC" + String.format("%010d", number), 'j', "Disponible");
        if (number % 10 == 0) add(record, "590", ' ', ' ', 'a', "Nota local de la Biblioteca Central.");
        if (number % 11 == 0) add(record, "999", ' ', ' ', 'a', "Campo local preservado", 'b', "valor " + number);
        return record;
    }

    private void add(Record record, String tag, char ind1, char ind2, Object... codeValues) {
        DataField field = factory.newDataField(tag, ind1, ind2);
        for (int i = 0; i < codeValues.length; i += 2) {
            char code = (Character) codeValues[i];
            String value = String.valueOf(codeValues[i + 1]);
            field.addSubfield(factory.newSubfield(code, value));
        }
        record.addVariableField(field);
    }

    private String field008(int year, String language) {
        return "260806s" + year + "    mx ||||| |||||000 0 " + language + " d";
    }

    private String choose(List<String> values) { return values.get(random.nextInt(values.size())); }

    private String naturalName(String inverted) {
        String[] parts = inverted.split(",", 2);
        return parts.length == 2 ? parts[1].trim() + " " + parts[0].trim() : inverted;
    }

    private String publicationPlace(String publisher) {
        if (publisher.contains("Colima")) return "Colima, México";
        if (publisher.contains("O'Reilly")) return "Sebastopol, California";
        if (publisher.contains("Addison")) return "Boston";
        if (publisher.contains("Springer")) return "Cham, Suiza";
        return "Ciudad de México";
    }

    private String subjectFor(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("java")) return "Java (Lenguaje de programación)";
        if (lower.contains("bibframe") || lower.contains("biblioteca")) return "Bibliotecas digitales";
        if (lower.contains("inteligencia")) return "Inteligencia artificial";
        if (lower.contains("historia")) return "Historia de México";
        if (lower.contains("teología")) return "Teología";
        if (lower.contains("matemáticas")) return "Matemáticas";
        return "Literatura mexicana";
    }

    private String deweyFor(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("java")) return "005.133";
        if (lower.contains("bibframe") || lower.contains("biblioteca")) return "025.32";
        if (lower.contains("inteligencia")) return "006.3";
        if (lower.contains("historia")) return "972.3";
        if (lower.contains("teología")) return "230";
        if (lower.contains("matemáticas")) return "510";
        return "860";
    }

    private String isbn13(int number) {
        String body = "978607" + String.format("%06d", number % 1_000_000);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = body.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return body + ((10 - (sum % 10)) % 10);
    }
}
