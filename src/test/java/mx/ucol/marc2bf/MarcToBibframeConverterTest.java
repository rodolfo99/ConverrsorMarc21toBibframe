package mx.ucol.marc2bf;

import mx.ucol.marc2bf.bibframe.MappingReport;
import mx.ucol.marc2bf.bibframe.MarcToBibframeConverter;
import mx.ucol.marc2bf.bibframe.Vocab;
import mx.ucol.marc2bf.generator.SyntheticMarcGenerator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.marc4j.marc.Record;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarcToBibframeConverterTest {
    @Test
    void convertsWorkInstanceTitleIsbnAndItem() {
        Record record = new SyntheticMarcGenerator(12345L).createRecord(1);
        MappingReport report = new MappingReport();
        MarcToBibframeConverter converter = new MarcToBibframeConverter("https://example.org/bf/", report);

        Model model = converter.convert(record);
        try {
            assertTrue(model.contains(null, RDF.type, model.createResource(Vocab.BF + "Work")));
            assertTrue(model.contains(null, RDF.type, model.createResource(Vocab.BF + "Instance")));
            assertTrue(model.contains(null, RDF.type, model.createResource(Vocab.BF + "Item")));
            assertTrue(model.contains(null, model.createProperty(Vocab.BF, "mainTitle")));
            assertTrue(model.contains(null, RDF.type, model.createResource(Vocab.BF + "Isbn")));
            assertTrue(model.size() > 40);
        } finally {
            model.close();
        }
    }

    @Test
    void preservesUnknownLocalField() {
        Record record = new SyntheticMarcGenerator(12345L).createRecord(11);
        MarcToBibframeConverter converter = new MarcToBibframeConverter("https://example.org/bf/");
        Model model = converter.convert(record);
        try {
            assertTrue(model.contains(null, model.createProperty(Vocab.MARC, "tag"), "999"));
        } finally {
            model.close();
        }
    }
}
