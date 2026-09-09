package mx.ucol.marc2bf.bibframe;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/** Namespaces and helpers used by the converter. */
public final class Vocab {
    public static final String BF = "http://id.loc.gov/ontologies/bibframe/";
    public static final String BFLC = "http://id.loc.gov/ontologies/bflc/";
    public static final String MADS = "http://www.loc.gov/mads/rdf/v1#";
    public static final String RELATORS = "http://id.loc.gov/vocabulary/relators/";
    public static final String LANGUAGES = "http://id.loc.gov/vocabulary/languages/";
    public static final String COUNTRIES = "http://id.loc.gov/vocabulary/countries/";
    public static final String CONTENT_TYPES = "http://id.loc.gov/vocabulary/contentTypes/";
    public static final String MEDIA_TYPES = "http://id.loc.gov/vocabulary/mediaTypes/";
    public static final String CARRIERS = "http://id.loc.gov/vocabulary/carriers/";
    public static final String MARC = "https://bibliotecas.ucol.mx/vocab/marc/";

    private Vocab() {}

    public static Resource bfClass(Model model, String localName) {
        return model.createResource(BF + localName);
    }

    public static Property bfProperty(Model model, String localName) {
        return model.createProperty(BF, localName);
    }

    public static Resource bflcClass(Model model, String localName) {
        return model.createResource(BFLC + localName);
    }

    public static Property bflcProperty(Model model, String localName) {
        return model.createProperty(BFLC, localName);
    }

    public static Property marcProperty(Model model, String localName) {
        return model.createProperty(MARC, localName);
    }
}
