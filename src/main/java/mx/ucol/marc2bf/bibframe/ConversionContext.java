package mx.ucol.marc2bf.bibframe;

import mx.ucol.marc2bf.util.UriMinter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.marc4j.marc.Record;

import java.util.HashSet;
import java.util.Set;

/** Mutable state used while converting one MARC record. */
public final class ConversionContext {
    private final Record record;
    private final Model model;
    private final UriMinter uris;
    private final MappingReport report;
    private final String key;
    private final Resource work;
    private final Resource instance;
    private Resource adminMetadata;
    private Resource item;
    private final Set<String> mappedTags = new HashSet<>();
    private int sequence;

    public ConversionContext(Record record, Model model, UriMinter uris, MappingReport report, String key) {
        this.record = record;
        this.model = model;
        this.uris = uris;
        this.report = report;
        this.key = key;
        this.work = model.createResource(uris.uri("work", key));
        this.instance = model.createResource(uris.uri("instance", key));
        work.addProperty(RDF.type, Vocab.bfClass(model, "Work"));
        instance.addProperty(RDF.type, Vocab.bfClass(model, "Instance"));
        work.addProperty(Vocab.bfProperty(model, "hasInstance"), instance);
        instance.addProperty(Vocab.bfProperty(model, "instanceOf"), work);
    }

    public Record record() { return record; }
    public Model model() { return model; }
    public UriMinter uris() { return uris; }
    public MappingReport report() { return report; }
    public String key() { return key; }
    public Resource work() { return work; }
    public Resource instance() { return instance; }

    public Resource adminMetadata() {
        if (adminMetadata == null) {
            adminMetadata = model.createResource(uris.uri("admin", key));
            adminMetadata.addProperty(RDF.type, Vocab.bfClass(model, "AdminMetadata"));
            work.addProperty(Vocab.bfProperty(model, "adminMetadata"), adminMetadata);
            instance.addProperty(Vocab.bfProperty(model, "adminMetadata"), adminMetadata);
        }
        return adminMetadata;
    }

    public Resource item() {
        if (item == null) {
            item = model.createResource(uris.uri("item", key));
            item.addProperty(RDF.type, Vocab.bfClass(model, "Item"));
            item.addProperty(Vocab.bfProperty(model, "itemOf"), instance);
            instance.addProperty(Vocab.bfProperty(model, "hasItem"), item);
        }
        return item;
    }

    public String nextKey(String type) {
        return key + "-" + type + "-" + (++sequence);
    }

    public void markMapped(String tag) {
        mappedTags.add(tag);
        report.mapped(tag);
    }

    public boolean wasMapped(String tag) {
        return mappedTags.contains(tag);
    }
}
