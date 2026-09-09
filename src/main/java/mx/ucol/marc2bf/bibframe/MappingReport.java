package mx.ucol.marc2bf.bibframe;

import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Aggregated conversion statistics. */
public final class MappingReport {
    private final LongAdder records = new LongAdder();
    private final AtomicLong triples = new AtomicLong();
    private final Map<String, LongAdder> encountered = new TreeMap<>();
    private final Map<String, LongAdder> mapped = new TreeMap<>();
    private final Map<String, LongAdder> preserved = new TreeMap<>();

    public void recordEncountered(Record record) {
        records.increment();
        for (VariableField field : record.getVariableFields()) {
            encountered.computeIfAbsent(field.getTag(), ignored -> new LongAdder()).increment();
        }
    }

    public void mapped(String tag) {
        mapped.computeIfAbsent(tag, ignored -> new LongAdder()).increment();
    }

    public void preserved(String tag) {
        preserved.computeIfAbsent(tag, ignored -> new LongAdder()).increment();
    }

    public void addTriples(long count) {
        triples.addAndGet(count);
    }

    public void setTriples(long count) {
        triples.set(count);
    }

    public long records() { return records.sum(); }
    public long triples() { return triples.get(); }

    public void writeCsv(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
        StringBuilder csv = new StringBuilder("tag,encountered,mapped,preserved_as_local\n");
        for (String tag : encountered.keySet()) {
            csv.append(tag).append(',')
                    .append(sum(encountered, tag)).append(',')
                    .append(sum(mapped, tag)).append(',')
                    .append(sum(preserved, tag)).append('\n');
        }
        csv.append("TOTAL_RECORDS,").append(records()).append(",,\n");
        csv.append("TOTAL_TRIPLES,").append(triples()).append(",,\n");
        Files.writeString(absolute, csv, StandardCharsets.UTF_8);
    }

    private static long sum(Map<String, LongAdder> map, String key) {
        LongAdder value = map.get(key);
        return value == null ? 0L : value.sum();
    }
}
