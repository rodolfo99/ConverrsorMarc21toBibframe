package mx.ucol.marc2bf.bibframe;

import mx.ucol.marc2bf.util.MarcUtil;
import mx.ucol.marc2bf.util.UriMinter;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Leader;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static mx.ucol.marc2bf.bibframe.Vocab.*;

/**
 * Pure-Java MARC21 bibliographic to BIBFRAME converter.
 *
 * <p>The converter maps the most frequently used semantic fields to BIBFRAME
 * Work, Instance, Item, Agent, Title, Identifier, Classification, Subject,
 * ProvisionActivity and Note resources. Any field without a dedicated mapping
 * is preserved as a local RDF node so the source data is not silently lost.</p>
 */
public final class MarcToBibframeConverter {
    private static final Set<Character> CONTROL_SUBFIELDS = Set.of('0', '1', '2', '3', '5', '6', '7', '8');
    private static final Set<String> NAME_TAGS = Set.of("100", "110", "111", "700", "710", "711");
    private static final Set<String> TITLE_TAGS = Set.of("130", "210", "222", "240", "242", "243", "245", "246", "247", "730", "740");
    private static final Set<String> IDENTIFIER_TAGS = Set.of(
            "010", "013", "015", "016", "017", "018", "020", "022", "023", "024", "025", "026",
            "027", "028", "030", "032", "035", "036", "037", "074", "088");
    private static final Set<String> CLASSIFICATION_TAGS = Set.of(
            "050", "051", "052", "055", "060", "061", "070", "071", "072", "080", "082", "083",
            "084", "085", "086");
    private static final Set<String> SUBJECT_TAGS = Set.of(
            "600", "610", "611", "630", "647", "648", "650", "651", "653", "654", "655", "656",
            "657", "658", "662", "688");
    private static final Set<String> SERIES_TAGS = Set.of("440", "490", "800", "810", "811", "830");
    private static final Set<String> LINKING_TAGS = Set.of(
            "760", "762", "765", "767", "770", "772", "773", "774", "775", "776", "777", "780",
            "785", "786", "787");
    private static final Set<String> ITEM_TAGS = Set.of(
            "841", "842", "843", "844", "845", "850", "852", "853", "854", "855", "856", "863",
            "864", "865", "866", "867", "868", "876", "877", "878");
    private static final Set<String> PHYSICAL_TAGS = Set.of(
            "300", "306", "307", "310", "321", "336", "337", "338", "340", "341", "342", "343",
            "344", "345", "346", "347", "348", "351", "352", "355", "357", "362", "363", "365",
            "366", "370", "377", "380", "381", "382", "383", "384");

    private static final Map<String, String> IDENTIFIER_CLASSES = identifierClasses();
    private static final Map<String, String> CLASSIFICATION_CLASSES = classificationClasses();
    private static final Map<String, String> LINK_PROPERTIES = linkingProperties();
    private static final Map<String, String> NOTE_PROPERTIES = noteProperties();

    private final UriMinter uris;
    private final MappingReport report;

    public MarcToBibframeConverter(String baseUri, MappingReport report) {
        this.uris = new UriMinter(baseUri);
        this.report = Objects.requireNonNull(report, "MappingReport no puede ser nulo.");
    }

    public MarcToBibframeConverter(String baseUri) {
        this(baseUri, new MappingReport());
    }

    public MappingReport report() {
        return report;
    }

    public Model convert(Record record) {
        Objects.requireNonNull(record, "El registro MARC21 no puede ser nulo.");
        report.recordEncountered(record);
        Model model = createModel();
        String key = recordKey(record);
        ConversionContext context = new ConversionContext(record, model, uris, report, key);

        mapLeader(context);
        mapControlFields(context);
        for (VariableField field : record.getVariableFields()) {
            if (field instanceof DataField dataField) {
                mapDataField(context, dataField);
            }
        }
        return model;
    }

    private Model createModel() {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("bf", BF);
        model.setNsPrefix("bflc", BFLC);
        model.setNsPrefix("madsrdf", MADS);
        model.setNsPrefix("relators", RELATORS);
        model.setNsPrefix("lang", LANGUAGES);
        model.setNsPrefix("marc", MARC);
        model.setNsPrefix("rdf", RDF.getURI());
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("catalog", uris.baseUri());
        return model;
    }

    private String recordKey(Record record) {
        String control = MarcUtil.control(record, "001");
        if (!control.isBlank()) return control;
        return "record-" + UriMinter.token(record.toString());
    }

    private void mapLeader(ConversionContext c) {
        Leader leader = c.record().getLeader();
        if (leader == null) return;
        String raw = leader.toString();
        c.adminMetadata().addProperty(marcProperty(c.model(), "leader"), raw);
        c.report().mapped("LDR");

        char type = leader.getTypeOfRecord();
        Resource workType = switch (type) {
            case 'c', 'd' -> bfClass(c.model(), "NotatedMusic");
            case 'e', 'f' -> bfClass(c.model(), "Cartography");
            case 'g' -> bfClass(c.model(), "MovingImage");
            case 'i', 'j' -> bfClass(c.model(), "Audio");
            case 'k' -> bfClass(c.model(), "StillImage");
            case 'm' -> bfClass(c.model(), "Multimedia");
            case 'o', 'p' -> bfClass(c.model(), "MixedMaterial");
            case 'r' -> bfClass(c.model(), "Object");
            case 't' -> bfClass(c.model(), "Manuscript");
            default -> bfClass(c.model(), "Text");
        };
        c.work().addProperty(RDF.type, workType);
        c.adminMetadata().addProperty(marcProperty(c.model(), "recordStatus"), String.valueOf(leader.getRecordStatus()));
        c.adminMetadata().addProperty(marcProperty(c.model(), "bibliographicLevel"), String.valueOf(leader.getImplDefined1()[0]));
        c.adminMetadata().addProperty(marcProperty(c.model(), "encodingLevel"), String.valueOf(leader.getImplDefined2()[0]));
    }

    private void mapControlFields(ConversionContext c) {
        String field001 = MarcUtil.control(c.record(), "001");
        String field003 = MarcUtil.control(c.record(), "003");
        if (!field001.isBlank()) {
            Resource local = c.model().createResource(c.uris().uri("identifier/local", c.key()));
            local.addProperty(RDF.type, bfClass(c.model(), "Local"));
            local.addProperty(RDF.value, field001);
            if (!field003.isBlank()) local.addProperty(bfProperty(c.model(), "source"), field003);
            c.instance().addProperty(bfProperty(c.model(), "identifiedBy"), local);
            c.markMapped("001");
            if (!field003.isBlank()) c.markMapped("003");
        }

        String field005 = MarcUtil.control(c.record(), "005");
        if (!field005.isBlank()) {
            c.adminMetadata().addProperty(bfProperty(c.model(), "changeDate"), field005);
            c.markMapped("005");
        }

        String field008 = MarcUtil.control(c.record(), "008");
        if (!field008.isBlank()) {
            map008(c, field008);
            c.adminMetadata().addProperty(marcProperty(c.model(), "field008"), field008);
            c.markMapped("008");
        }

        for (String tag : List.of("006", "007")) {
            for (VariableField field : c.record().getVariableFields(tag)) {
                if (field instanceof ControlField cf) {
                    Resource coded = c.model().createResource(c.uris().uri("coded-field", c.nextKey(tag)));
                    coded.addProperty(RDF.type, c.model().createResource(MARC + "CodedData"));
                    coded.addProperty(marcProperty(c.model(), "tag"), tag);
                    coded.addProperty(RDF.value, MarcUtil.safe(cf.getData()));
                    c.instance().addProperty(marcProperty(c.model(), "sourceField"), coded);
                    c.markMapped(tag);
                }
            }
        }
    }

    private void map008(ConversionContext c, String value) {
        if (value.length() >= 15) {
            String dateType = value.substring(6, 7);
            String date1 = value.substring(7, 11).replace('u', '?');
            String date2 = value.substring(11, 15).replace('u', '?');
            if (!date1.isBlank()) c.instance().addProperty(bfProperty(c.model(), "provisionActivityStatement"), dateType + ":" + date1 + (date2.isBlank() ? "" : "/" + date2));
        }
        if (value.length() >= 18) {
            String country = value.substring(15, 18).trim().toLowerCase(Locale.ROOT);
            if (!country.isBlank() && !country.contains("|")) {
                c.instance().addProperty(bfProperty(c.model(), "place"), c.model().createResource(COUNTRIES + country));
            }
        }
        if (value.length() >= 38) {
            String language = value.substring(35, 38).trim().toLowerCase(Locale.ROOT);
            if (!language.isBlank() && !"und".equals(language) && !language.contains("|")) {
                c.work().addProperty(bfProperty(c.model(), "language"), c.model().createResource(LANGUAGES + language));
            }
        }
    }

    private void mapDataField(ConversionContext c, DataField field) {
        String tag = field.getTag();
        if (IDENTIFIER_TAGS.contains(tag)) {
            mapIdentifier(c, field);
        } else if (CLASSIFICATION_TAGS.contains(tag)) {
            mapClassification(c, field);
        } else if (NAME_TAGS.contains(tag)) {
            mapContribution(c, field);
        } else if (TITLE_TAGS.contains(tag)) {
            mapTitle(c, field);
        } else if (SUBJECT_TAGS.contains(tag)) {
            mapSubject(c, field);
        } else if (SERIES_TAGS.contains(tag)) {
            mapSeries(c, field);
        } else if (LINKING_TAGS.contains(tag)) {
            mapLinkingEntry(c, field);
        } else if (ITEM_TAGS.contains(tag)) {
            mapItem(c, field);
        } else if (PHYSICAL_TAGS.contains(tag)) {
            mapPhysical(c, field);
        } else if (tag.startsWith("5")) {
            mapNote(c, field);
        } else {
            switch (tag) {
                case "033", "034", "043", "044", "045", "046", "047", "048" -> mapCodedData(c, field);
                case "040", "042", "883", "884", "885" -> mapAdminData(c, field);
                case "041" -> mapLanguage(c, field);
                case "250", "251" -> mapEdition(c, field);
                case "254", "255", "256", "257", "258", "263", "270" -> mapInstanceStatement(c, field);
                case "260", "264" -> mapProvision(c, field);
                case "880" -> mapAlternateScript(c, field);
                default -> preserveLocal(c, field);
            }
        }
    }

    private void mapIdentifier(ConversionContext c, DataField field) {
        String tag = field.getTag();
        List<String> values = new ArrayList<>();
        for (char code : identifierValueCodes(tag)) values.addAll(MarcUtil.subfields(field, code));
        if (values.isEmpty()) {
            preserveLocal(c, field);
            return;
        }
        String className = IDENTIFIER_CLASSES.getOrDefault(tag, "Identifier");
        for (String raw : values) {
            String value = "020".equals(tag) ? MarcUtil.normalizeIsbn(raw) : MarcUtil.cleanPunctuation(raw);
            if (value.isBlank()) continue;
            Resource identifier = c.model().createResource(c.uris().uri("identifier/" + className.toLowerCase(Locale.ROOT), c.nextKey(tag) + value));
            identifier.addProperty(RDF.type, bfClass(c.model(), className));
            identifier.addProperty(RDF.value, value);
            String qualifier = MarcUtil.subfield(field, 'q');
            if (!qualifier.isBlank()) identifier.addProperty(bfProperty(c.model(), "qualifier"), MarcUtil.cleanPunctuation(qualifier));
            for (String canceled : MarcUtil.subfields(field, 'z')) identifier.addProperty(bfProperty(c.model(), "status"), "cancelado: " + canceled);
            c.instance().addProperty(bfProperty(c.model(), "identifiedBy"), identifier);
        }
        c.markMapped(tag);
    }

    private char[] identifierValueCodes(String tag) {
        return switch (tag) {
            case "010", "015", "016", "017", "020", "022", "023", "024", "025", "026", "027", "028", "030", "032", "035", "036", "074", "088" -> new char[]{'a'};
            case "013" -> new char[]{'a', 'b'};
            case "018" -> new char[]{'a'};
            case "037" -> new char[]{'a'};
            default -> new char[]{'a'};
        };
    }

    private void mapClassification(ConversionContext c, DataField field) {
        String number = MarcUtil.join(field, 'a', 'b');
        if (number.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        String className = CLASSIFICATION_CLASSES.getOrDefault(field.getTag(), "Classification");
        Resource classification = c.model().createResource(c.uris().uri("classification", c.nextKey(field.getTag()) + number));
        classification.addProperty(RDF.type, bfClass(c.model(), className));
        classification.addProperty(bfProperty(c.model(), "classificationPortion"), number);
        String itemPortion = MarcUtil.join(field, 'c', 'd');
        if (!itemPortion.isBlank()) classification.addProperty(bfProperty(c.model(), "itemPortion"), itemPortion);
        String edition = MarcUtil.subfield(field, '2');
        if (!edition.isBlank()) classification.addProperty(bfProperty(c.model(), "edition"), edition);
        String source = MarcUtil.subfield(field, '2');
        if (!source.isBlank()) classification.addProperty(bfProperty(c.model(), "source"), source);
        c.work().addProperty(bfProperty(c.model(), "classification"), classification);
        c.markMapped(field.getTag());
    }

    private void mapContribution(ConversionContext c, DataField field) {
        String tag = field.getTag();
        String label = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (label.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        String agentClass = switch (tag.charAt(1)) {
            case '0' -> "Person";
            case '1' -> "Organization";
            case '2' -> "Meeting";
            default -> "Agent";
        };
        String authorityUri = firstUri(field);
        Resource agent = authorityUri.isBlank()
                ? c.model().createResource(c.uris().uri("agent", label))
                : c.model().createResource(authorityUri);
        agent.addProperty(RDF.type, bfClass(c.model(), agentClass));
        agent.addProperty(RDFS.label, label);

        Resource contribution = c.model().createResource(c.uris().uri("contribution", c.nextKey(tag)));
        contribution.addProperty(RDF.type, bfClass(c.model(), "Contribution"));
        contribution.addProperty(bfProperty(c.model(), "agent"), agent);
        List<String> roles = roleCodes(field, tag.startsWith("1") ? "aut" : "ctb");
        for (String role : roles) contribution.addProperty(bfProperty(c.model(), "role"), c.model().createResource(RELATORS + role));
        c.work().addProperty(bfProperty(c.model(), "contribution"), contribution);

        String title = MarcUtil.subfield(field, 't');
        if (!title.isBlank()) {
            Resource relatedWork = c.model().createResource(c.uris().uri("work", label + " " + title));
            relatedWork.addProperty(RDF.type, bfClass(c.model(), "Work"));
            relatedWork.addProperty(RDFS.label, MarcUtil.cleanPunctuation(title));
            c.work().addProperty(bfProperty(c.model(), "relatedTo"), relatedWork);
        }
        c.markMapped(tag);
    }

    private List<String> roleCodes(DataField field, String defaultRole) {
        List<String> result = new ArrayList<>();
        for (String role : MarcUtil.subfields(field, '4')) {
            String normalized = MarcUtil.normalizeCode(role);
            if (!normalized.isBlank()) result.add(normalized);
        }
        if (result.isEmpty()) {
            for (String term : MarcUtil.subfields(field, 'e')) {
                result.add(relatorCode(term));
            }
        }
        if (result.isEmpty()) result.add(defaultRole);
        return MarcUtil.distinct(result);
    }

    private String relatorCode(String term) {
        String value = MarcUtil.cleanPunctuation(term).toLowerCase(Locale.ROOT);
        if (value.contains("trad")) return "trl";
        if (value.contains("edit")) return "edt";
        if (value.contains("compil")) return "com";
        if (value.contains("coord")) return "ctb";
        if (value.contains("ilustr")) return "ill";
        if (value.contains("prolog")) return "aui";
        if (value.contains("director")) return "drt";
        if (value.contains("product")) return "pro";
        if (value.contains("autor")) return "aut";
        return "ctb";
    }

    private void mapTitle(ConversionContext c, DataField field) {
        String tag = field.getTag();
        String mainTitle = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'a'));
        if (mainTitle.isBlank()) mainTitle = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 't'));
        String subtitle = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'b'));
        String partNumber = MarcUtil.cleanPunctuation(MarcUtil.join(field, 'n'));
        String partName = MarcUtil.cleanPunctuation(MarcUtil.join(field, 'p'));
        if (mainTitle.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        String titleClass = switch (tag) {
            case "246", "247" -> "VariantTitle";
            case "210" -> "AbbreviatedTitle";
            case "222" -> "KeyTitle";
            case "240", "243", "130", "730" -> "Title";
            default -> "Title";
        };
        Resource title = c.model().createResource(c.uris().uri("title", c.nextKey(tag) + mainTitle));
        title.addProperty(RDF.type, bfClass(c.model(), titleClass));
        title.addProperty(bfProperty(c.model(), "mainTitle"), mainTitle);
        if (!subtitle.isBlank()) title.addProperty(bfProperty(c.model(), "subtitle"), subtitle);
        if (!partNumber.isBlank()) title.addProperty(bfProperty(c.model(), "partNumber"), partNumber);
        if (!partName.isBlank()) title.addProperty(bfProperty(c.model(), "partName"), partName);
        title.addProperty(RDFS.label, titleLabel(mainTitle, subtitle, partNumber, partName));

        if (Set.of("246", "247", "210", "222", "242").contains(tag)) {
            c.work().addProperty(bfProperty(c.model(), "title"), title);
        } else {
            c.work().addProperty(bfProperty(c.model(), "title"), title);
            if ("245".equals(tag)) c.instance().addProperty(bfProperty(c.model(), "title"), title);
        }
        if ("245".equals(tag)) {
            String responsibility = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'c'));
            if (!responsibility.isBlank()) c.instance().addProperty(bfProperty(c.model(), "responsibilityStatement"), responsibility);
            title.addLiteral(marcProperty(c.model(), "nonfilingCharacters"), Character.digit(field.getIndicator2(), 10));
        }
        c.markMapped(tag);
    }

    private String titleLabel(String main, String subtitle, String number, String part) {
        StringBuilder value = new StringBuilder(main);
        if (!subtitle.isBlank()) value.append(": ").append(subtitle);
        if (!number.isBlank()) value.append(". ").append(number);
        if (!part.isBlank()) value.append(". ").append(part);
        return value.toString();
    }

    private void mapLanguage(ConversionContext c, DataField field) {
        Map<Character, String> predicates = Map.ofEntries(
                Map.entry('a', "language"), Map.entry('b', "language"), Map.entry('d', "language"),
                Map.entry('e', "language"), Map.entry('f', "language"), Map.entry('g', "language"),
                Map.entry('h', "originalLanguage"), Map.entry('j', "language"), Map.entry('k', "language"),
                Map.entry('m', "language"), Map.entry('n', "language"), Map.entry('p', "language"),
                Map.entry('q', "language"), Map.entry('r', "language"), Map.entry('t', "language"));
        boolean mapped = false;
        for (Subfield sf : field.getSubfields()) {
            String predicate = predicates.get(sf.getCode());
            String code = MarcUtil.normalizeCode(sf.getData());
            if (predicate != null && !code.isBlank()) {
                c.work().addProperty(bfProperty(c.model(), predicate), c.model().createResource(LANGUAGES + code));
                mapped = true;
            }
        }
        if (mapped) c.markMapped(field.getTag()); else preserveLocal(c, field);
    }

    private void mapProvision(ConversionContext c, DataField field) {
        String type = "260".equals(field.getTag()) ? "Publication" : switch (field.getIndicator2()) {
            case '0' -> "Production";
            case '1' -> "Publication";
            case '2' -> "Distribution";
            case '3' -> "Manufacture";
            case '4' -> "Copyright";
            default -> "ProvisionActivity";
        };
        Resource activity = c.model().createResource(c.uris().uri("provision", c.nextKey(field.getTag())));
        activity.addProperty(RDF.type, bfClass(c.model(), type));
        boolean hasData = false;
        for (String placeValue : MarcUtil.subfields(field, 'a')) {
            String placeName = MarcUtil.cleanPunctuation(placeValue);
            if (!placeName.isBlank()) {
                Resource place = c.model().createResource(c.uris().uri("place", placeName));
                place.addProperty(RDF.type, bfClass(c.model(), "Place"));
                place.addProperty(RDFS.label, placeName);
                activity.addProperty(bfProperty(c.model(), "place"), place);
                hasData = true;
            }
        }
        for (String agentValue : MarcUtil.subfields(field, 'b')) {
            String agentName = MarcUtil.cleanPunctuation(agentValue);
            if (!agentName.isBlank()) {
                Resource agent = c.model().createResource(c.uris().uri("agent", agentName));
                agent.addProperty(RDF.type, bfClass(c.model(), "Organization"));
                agent.addProperty(RDFS.label, agentName);
                activity.addProperty(bfProperty(c.model(), "agent"), agent);
                hasData = true;
            }
        }
        for (String date : MarcUtil.subfields(field, 'c')) {
            String cleaned = MarcUtil.cleanPunctuation(date);
            if (!cleaned.isBlank()) {
                activity.addProperty(bfProperty(c.model(), "date"), cleaned);
                hasData = true;
            }
        }
        if (hasData) {
            c.instance().addProperty(bfProperty(c.model(), "provisionActivity"), activity);
            c.instance().addProperty(bfProperty(c.model(), "provisionActivityStatement"), MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS));
            c.markMapped(field.getTag());
        } else preserveLocal(c, field);
    }

    private void mapEdition(ConversionContext c, DataField field) {
        String value = MarcUtil.join(field, 'a', 'b');
        if (value.isBlank()) preserveLocal(c, field);
        else {
            c.instance().addProperty(bfProperty(c.model(), "editionStatement"), value);
            c.markMapped(field.getTag());
        }
    }

    private void mapInstanceStatement(ConversionContext c, DataField field) {
        String value = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (value.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        String property = switch (field.getTag()) {
            case "254" -> "musicFormat";
            case "255" -> "cartographicAttributes";
            case "256" -> "digitalCharacteristic";
            case "257" -> "originPlace";
            case "263" -> "projectedProvisionDate";
            default -> "note";
        };
        if (Set.of("254", "255", "256", "257", "263").contains(field.getTag())) {
            c.instance().addProperty(bfProperty(c.model(), property), value);
        } else addNoteResource(c, c.instance(), value, "Nota MARC " + field.getTag(), field.getTag());
        c.markMapped(field.getTag());
    }

    private void mapPhysical(ConversionContext c, DataField field) {
        String tag = field.getTag();
        String value = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (value.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        switch (tag) {
            case "300" -> {
                String extentValue = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'a'));
                if (!extentValue.isBlank()) {
                    Resource extent = c.model().createResource(c.uris().uri("extent", c.nextKey(tag)));
                    extent.addProperty(RDF.type, bfClass(c.model(), "Extent"));
                    extent.addProperty(RDFS.label, extentValue);
                    c.instance().addProperty(bfProperty(c.model(), "extent"), extent);
                }
                String dimensions = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'c'));
                if (!dimensions.isBlank()) c.instance().addProperty(bfProperty(c.model(), "dimensions"), dimensions);
                String details = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'b'));
                if (!details.isBlank()) c.instance().addProperty(bfProperty(c.model(), "physicalDescription"), details);
                String accompanying = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'e'));
                if (!accompanying.isBlank()) c.instance().addProperty(bfProperty(c.model(), "accompaniedBy"), accompanying);
            }
            case "306" -> c.instance().addProperty(bfProperty(c.model(), "duration"), value);
            case "310" -> c.instance().addProperty(bfProperty(c.model(), "frequency"), value);
            case "321" -> c.instance().addProperty(bfProperty(c.model(), "frequency"), "anterior: " + value);
            case "336" -> mapRdaTerm(c, field, "content", CONTENT_TYPES, "Content");
            case "337" -> mapRdaTerm(c, field, "media", MEDIA_TYPES, "Media");
            case "338" -> mapRdaTerm(c, field, "carrier", CARRIERS, "Carrier");
            case "340" -> addNoteResource(c, c.instance(), value, "Características físicas", tag);
            case "341" -> addNoteResource(c, c.instance(), value, "Accesibilidad", tag);
            case "342", "343" -> addNoteResource(c, c.instance(), value, "Datos geoespaciales", tag);
            case "344" -> addCharacteristic(c, "SoundCharacteristic", value);
            case "345" -> addCharacteristic(c, "ProjectionCharacteristic", value);
            case "346" -> addCharacteristic(c, "VideoCharacteristic", value);
            case "347" -> addCharacteristic(c, "DigitalCharacteristic", value);
            case "348" -> addCharacteristic(c, "MusicFormat", value);
            case "351" -> addNoteResource(c, c.work(), value, "Organización y arreglo", tag);
            case "352" -> addNoteResource(c, c.instance(), value, "Representación gráfica digital", tag);
            case "355", "357" -> addNoteResource(c, c.item(), value, "Seguridad y difusión", tag);
            case "362", "363" -> c.instance().addProperty(bfProperty(c.model(), "enumerationAndChronology"), value);
            case "365", "366" -> addNoteResource(c, c.instance(), value, "Precio o disponibilidad", tag);
            case "370" -> mapAssociatedPlaces(c, field);
            case "377" -> mapLanguage(c, field);
            case "380" -> c.work().addProperty(bfProperty(c.model(), "genreForm"), labeledResource(c, "genre", value, "GenreForm"));
            case "381" -> c.work().addProperty(bfProperty(c.model(), "natureOfContent"), value);
            case "382" -> c.work().addProperty(bfProperty(c.model(), "musicMedium"), value);
            case "383" -> c.work().addProperty(bfProperty(c.model(), "musicSerialNumber"), value);
            case "384" -> c.work().addProperty(bfProperty(c.model(), "musicKey"), value);
            default -> addNoteResource(c, c.instance(), value, "Descripción física", tag);
        }
        c.markMapped(tag);
    }

    private void mapRdaTerm(ConversionContext c, DataField field, String property, String vocabulary, String className) {
        String term = MarcUtil.cleanPunctuation(MarcUtil.subfield(field, 'a'));
        String code = MarcUtil.normalizeCode(MarcUtil.subfield(field, 'b'));
        Resource resource = code.isBlank()
                ? c.model().createResource(c.uris().uri(property, term))
                : c.model().createResource(vocabulary + code);
        resource.addProperty(RDF.type, bfClass(c.model(), className));
        if (!term.isBlank()) resource.addProperty(RDFS.label, term);
        c.instance().addProperty(bfProperty(c.model(), property), resource);
    }

    private void addCharacteristic(ConversionContext c, String className, String value) {
        Resource resource = c.model().createResource(c.uris().uri("characteristic", c.nextKey(className)));
        resource.addProperty(RDF.type, bfClass(c.model(), className));
        resource.addProperty(RDFS.label, value);
        c.instance().addProperty(bfProperty(c.model(), "characteristic"), resource);
    }

    private void mapAssociatedPlaces(ConversionContext c, DataField field) {
        for (Subfield sf : field.getSubfields()) {
            if (Character.isLetter(sf.getCode())) {
                String value = MarcUtil.cleanPunctuation(sf.getData());
                if (!value.isBlank()) c.work().addProperty(bfProperty(c.model(), "place"), labeledResource(c, "place", value, "Place"));
            }
        }
    }

    private void mapSeries(ConversionContext c, DataField field) {
        String label = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (label.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        Resource hub = c.model().createResource(c.uris().uri("hub", label));
        hub.addProperty(RDF.type, bfClass(c.model(), "Hub"));
        hub.addProperty(RDFS.label, label);
        c.work().addProperty(bfProperty(c.model(), "partOf"), hub);
        String enumeration = MarcUtil.subfield(field, 'v');
        if (!enumeration.isBlank()) c.work().addProperty(bfProperty(c.model(), "seriesEnumeration"), MarcUtil.cleanPunctuation(enumeration));
        c.markMapped(field.getTag());
    }

    private void mapNote(ConversionContext c, DataField field) {
        String value = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (value.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        String tag = field.getTag();
        switch (tag) {
            case "502" -> {
                Resource dissertation = labeledResource(c, "dissertation", value, "Dissertation");
                c.work().addProperty(bfProperty(c.model(), "dissertation"), dissertation);
            }
            case "506" -> c.item().addProperty(bfProperty(c.model(), "usageAndAccessPolicy"), policyResource(c, value, "AccessPolicy"));
            case "520" -> c.work().addProperty(bfProperty(c.model(), "summary"), labeledResource(c, "summary", value, "Summary"));
            case "521" -> c.work().addProperty(bfProperty(c.model(), "intendedAudience"), labeledResource(c, "audience", value, "IntendedAudience"));
            case "530" -> c.instance().addProperty(bfProperty(c.model(), "otherPhysicalFormat"), value);
            case "532" -> addNoteResource(c, c.instance(), value, "Nota de accesibilidad", tag);
            case "536" -> c.work().addProperty(bfProperty(c.model(), "grantingInstitution"), value);
            case "538" -> c.instance().addProperty(bfProperty(c.model(), "systemRequirement"), value);
            case "540" -> c.item().addProperty(bfProperty(c.model(), "usageAndAccessPolicy"), policyResource(c, value, "UsePolicy"));
            case "541" -> c.item().addProperty(bfProperty(c.model(), "acquisitionSource"), value);
            case "542" -> addNoteResource(c, c.item(), value, "Derechos de autor", tag);
            case "546" -> c.work().addProperty(bfProperty(c.model(), "language"), labeledResource(c, "language-note", value, "Language"));
            case "561" -> c.item().addProperty(bfProperty(c.model(), "custodialHistory"), value);
            case "562" -> addNoteResource(c, c.item(), value, "Identificación de copia", tag);
            case "563" -> c.item().addProperty(bfProperty(c.model(), "binding"), value);
            case "583" -> addNoteResource(c, c.item(), value, "Acción", tag);
            case "586" -> c.work().addProperty(bfProperty(c.model(), "award"), value);
            default -> {
                String property = NOTE_PROPERTIES.get(tag);
                Resource target = tag.compareTo("533") >= 0 && tag.compareTo("584") <= 0 ? c.item() : c.instance();
                if (property != null) target.addProperty(bfProperty(c.model(), property), value);
                else addNoteResource(c, target, value, "Nota MARC " + tag, tag);
            }
        }
        c.markMapped(tag);
    }

    private Resource policyResource(ConversionContext c, String value, String className) {
        return labeledResource(c, "policy", value, className);
    }

    private void mapSubject(ConversionContext c, DataField field) {
        String tag = field.getTag();
        String label = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (label.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        String className = switch (tag) {
            case "600" -> "Person";
            case "610" -> "Organization";
            case "611", "647" -> "Meeting";
            case "630" -> "Work";
            case "648" -> "Temporal";
            case "651", "662" -> "Place";
            case "655" -> "GenreForm";
            case "656" -> "Occupation";
            default -> "Topic";
        };
        String authorityUri = firstUri(field);
        Resource subject = authorityUri.isBlank()
                ? c.model().createResource(c.uris().uri("subject", label))
                : c.model().createResource(authorityUri);
        subject.addProperty(RDF.type, bfClass(c.model(), className));
        subject.addProperty(RDFS.label, label);
        String source = MarcUtil.subfield(field, '2');
        if (!source.isBlank()) subject.addProperty(bfProperty(c.model(), "source"), source);
        c.work().addProperty(bfProperty(c.model(), "subject"), subject);
        c.markMapped(tag);
    }

    private void mapLinkingEntry(ConversionContext c, DataField field) {
        String tag = field.getTag();
        String label = MarcUtil.joinAllSubfields(field, Set.of('6', '7', '8'));
        if (label.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        String control = MarcUtil.subfield(field, 'w');
        String isbn = MarcUtil.subfield(field, 'z');
        String issn = MarcUtil.subfield(field, 'x');
        String key = !control.isBlank() ? control : (!isbn.isBlank() ? isbn : (!issn.isBlank() ? issn : label));
        Resource related = c.model().createResource(c.uris().uri("related", key));
        related.addProperty(RDF.type, tag.equals("776") ? bfClass(c.model(), "Instance") : bfClass(c.model(), "Work"));
        related.addProperty(RDFS.label, label);
        if (!control.isBlank() || !isbn.isBlank() || !issn.isBlank()) {
            Resource id = c.model().createResource(c.uris().uri("identifier/related", key));
            id.addProperty(RDF.type, bfClass(c.model(), "Identifier"));
            id.addProperty(RDF.value, key);
            related.addProperty(bfProperty(c.model(), "identifiedBy"), id);
        }
        String property = LINK_PROPERTIES.getOrDefault(tag, "relatedTo");
        Resource source = tag.equals("776") ? c.instance() : c.work();
        source.addProperty(bfProperty(c.model(), property), related);
        c.markMapped(tag);
    }

    private void mapItem(ConversionContext c, DataField field) {
        String tag = field.getTag();
        String value = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (value.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        Resource item = c.item();
        switch (tag) {
            case "850" -> {
                for (String code : MarcUtil.subfields(field, 'a')) {
                    Resource heldBy = c.model().createResource(c.uris().uri("organization", code));
                    heldBy.addProperty(RDF.type, bfClass(c.model(), "Organization"));
                    heldBy.addProperty(RDFS.label, code);
                    item.addProperty(bfProperty(c.model(), "heldBy"), heldBy);
                }
            }
            case "852" -> mapLocation(c, field);
            case "856" -> mapElectronicLocator(c, field);
            case "876", "877", "878" -> mapItemIdentifier(c, field);
            case "863", "864", "865", "866", "867", "868" -> item.addProperty(bfProperty(c.model(), "enumerationAndChronology"), value);
            default -> addNoteResource(c, item, value, "Datos de existencias", tag);
        }
        c.markMapped(tag);
    }

    private void mapLocation(ConversionContext c, DataField field) {
        Resource location = c.model().createResource(c.uris().uri("location", c.nextKey("852")));
        location.addProperty(RDF.type, bfClass(c.model(), "Place"));
        String institution = MarcUtil.subfield(field, 'a');
        String sublocation = MarcUtil.subfield(field, 'b');
        String collection = MarcUtil.subfield(field, 'c');
        String label = String.join(" / ", MarcUtil.distinct(List.of(institution, sublocation, collection)));
        if (!label.isBlank()) location.addProperty(RDFS.label, label);
        c.item().addProperty(bfProperty(c.model(), "heldBy"), location);
        String shelf = MarcUtil.join(field, 'h', 'i', 'j', 'k', 'l', 'm');
        if (!shelf.isBlank()) {
            Resource shelfMark = labeledResource(c, "shelfmark", shelf, "ShelfMark");
            c.item().addProperty(bfProperty(c.model(), "shelfMark"), shelfMark);
        }
    }

    private void mapElectronicLocator(ConversionContext c, DataField field) {
        for (String url : MarcUtil.subfields(field, 'u')) {
            if (!url.isBlank()) c.instance().addProperty(bfProperty(c.model(), "electronicLocator"), c.model().createResource(url));
        }
        String access = MarcUtil.subfield(field, 'y');
        if (!access.isBlank()) c.instance().addProperty(bfProperty(c.model(), "supplementaryContent"), access);
    }

    private void mapItemIdentifier(ConversionContext c, DataField field) {
        for (char code : new char[]{'a', 'p', 'r'}) {
            for (String value : MarcUtil.subfields(field, code)) {
                Resource id = c.model().createResource(c.uris().uri("identifier/item", c.nextKey(field.getTag()) + value));
                id.addProperty(RDF.type, bfClass(c.model(), code == 'p' ? "Barcode" : "Local"));
                id.addProperty(RDF.value, value);
                c.item().addProperty(bfProperty(c.model(), "identifiedBy"), id);
            }
        }
        String status = MarcUtil.subfield(field, 'j');
        if (!status.isBlank()) c.item().addProperty(bfProperty(c.model(), "status"), status);
    }

    private void mapCodedData(ConversionContext c, DataField field) {
        String value = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (value.isBlank()) preserveLocal(c, field);
        else {
            Resource node = c.model().createResource(c.uris().uri("coded-data", c.nextKey(field.getTag())));
            node.addProperty(RDF.type, c.model().createResource(MARC + "CodedData"));
            node.addProperty(marcProperty(c.model(), "tag"), field.getTag());
            node.addProperty(RDF.value, value);
            c.work().addProperty(marcProperty(c.model(), "codedData"), node);
            c.markMapped(field.getTag());
        }
    }

    private void mapAdminData(ConversionContext c, DataField field) {
        String tag = field.getTag();
        String value = MarcUtil.joinAllSubfields(field, CONTROL_SUBFIELDS);
        if (value.isBlank()) {
            preserveLocal(c, field);
            return;
        }
        switch (tag) {
            case "040" -> {
                String source = MarcUtil.subfield(field, 'a');
                String catalogingLanguage = MarcUtil.subfield(field, 'b');
                String descriptionModifier = MarcUtil.subfield(field, 'd');
                String conventions = MarcUtil.subfield(field, 'e');
                if (!source.isBlank()) c.adminMetadata().addProperty(bfProperty(c.model(), "source"), source);
                if (!catalogingLanguage.isBlank()) c.adminMetadata().addProperty(bfProperty(c.model(), "descriptionLanguage"), c.model().createResource(LANGUAGES + MarcUtil.normalizeCode(catalogingLanguage)));
                if (!descriptionModifier.isBlank()) c.adminMetadata().addProperty(bfProperty(c.model(), "descriptionModifier"), descriptionModifier);
                if (!conventions.isBlank()) c.adminMetadata().addProperty(bfProperty(c.model(), "descriptionConventions"), conventions);
            }
            case "042" -> c.adminMetadata().addProperty(bfProperty(c.model(), "status"), value);
            case "883" -> c.adminMetadata().addProperty(marcProperty(c.model(), "metadataProvenance"), value);
            case "884" -> c.adminMetadata().addProperty(marcProperty(c.model(), "conversionInformation"), value);
            case "885" -> c.adminMetadata().addProperty(marcProperty(c.model(), "matchingInformation"), value);
            default -> c.adminMetadata().addProperty(marcProperty(c.model(), "administrativeData"), value);
        }
        c.markMapped(tag);
    }

    private void mapAlternateScript(ConversionContext c, DataField field) {
        String linkage = MarcUtil.subfield(field, '6');
        String linkedTag = linkage.length() >= 3 ? linkage.substring(0, 3) : "unknown";
        String value = MarcUtil.joinAllSubfields(field, Set.of('6', '8'));
        Resource alternate = c.model().createResource(c.uris().uri("alternate-script", c.nextKey("880")));
        alternate.addProperty(RDF.type, bfClass(c.model(), "Note"));
        alternate.addProperty(RDFS.label, value);
        alternate.addProperty(marcProperty(c.model(), "linkedTag"), linkedTag);
        alternate.addProperty(marcProperty(c.model(), "linkage"), linkage);
        c.work().addProperty(bfProperty(c.model(), "note"), alternate);
        c.markMapped("880");
    }

    private void preserveLocal(ConversionContext c, DataField field) {
        Resource local = c.model().createResource(c.uris().uri("marc-field", c.nextKey(field.getTag())));
        local.addProperty(RDF.type, c.model().createResource(MARC + "Field"));
        local.addProperty(marcProperty(c.model(), "tag"), field.getTag());
        local.addProperty(marcProperty(c.model(), "indicator1"), String.valueOf(field.getIndicator1()));
        local.addProperty(marcProperty(c.model(), "indicator2"), String.valueOf(field.getIndicator2()));
        local.addProperty(RDFS.label, MarcUtil.display(field));
        for (Subfield sf : field.getSubfields()) {
            Resource subfield = c.model().createResource(c.uris().uri("marc-subfield", c.nextKey(field.getTag() + sf.getCode())));
            subfield.addProperty(RDF.type, c.model().createResource(MARC + "Subfield"));
            subfield.addProperty(marcProperty(c.model(), "code"), String.valueOf(sf.getCode()));
            subfield.addProperty(RDF.value, MarcUtil.safe(sf.getData()));
            local.addProperty(marcProperty(c.model(), "subfield"), subfield);
        }
        c.work().addProperty(marcProperty(c.model(), "sourceField"), local);
        c.report().preserved(field.getTag());
    }

    private void addNoteResource(ConversionContext c, Resource target, String value, String noteType, String tag) {
        Resource note = c.model().createResource(c.uris().uri("note", c.nextKey(tag)));
        note.addProperty(RDF.type, bfClass(c.model(), "Note"));
        note.addProperty(RDFS.label, value);
        note.addProperty(bfProperty(c.model(), "noteType"), noteType);
        target.addProperty(bfProperty(c.model(), "note"), note);
    }

    private Resource labeledResource(ConversionContext c, String type, String label, String className) {
        Resource resource = c.model().createResource(c.uris().uri(type, label));
        resource.addProperty(RDF.type, bfClass(c.model(), className));
        resource.addProperty(RDFS.label, label);
        return resource;
    }

    private String firstUri(DataField field) {
        for (char code : new char[]{'1', '0'}) {
            for (String value : MarcUtil.subfields(field, code)) {
                String cleaned = value.replaceAll("^\\([^)]*\\)", "").trim();
                if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) return cleaned;
            }
        }
        return "";
    }

    private static Map<String, String> identifierClasses() {
        Map<String, String> map = new HashMap<>();
        map.put("010", "Lccn");
        map.put("013", "Identifier");
        map.put("015", "Nbn");
        map.put("016", "Identifier");
        map.put("017", "CopyrightNumber");
        map.put("018", "CopyrightNumber");
        map.put("020", "Isbn");
        map.put("022", "Issn");
        map.put("023", "Issn");
        map.put("024", "Identifier");
        map.put("025", "Identifier");
        map.put("026", "Fingerprint");
        map.put("027", "ReportNumber");
        map.put("028", "PublisherNumber");
        map.put("030", "Coden");
        map.put("032", "Identifier");
        map.put("035", "Local");
        map.put("036", "StudyNumber");
        map.put("037", "StockNumber");
        map.put("074", "GpoItemNumber");
        map.put("088", "ReportNumber");
        return Map.copyOf(map);
    }

    private static Map<String, String> classificationClasses() {
        Map<String, String> map = new HashMap<>();
        map.put("050", "ClassificationLcc");
        map.put("051", "ClassificationLcc");
        map.put("052", "Classification");
        map.put("055", "Classification");
        map.put("060", "ClassificationNlm");
        map.put("061", "ClassificationNlm");
        map.put("070", "Classification");
        map.put("071", "Classification");
        map.put("072", "Classification");
        map.put("080", "ClassificationUdc");
        map.put("082", "ClassificationDdc");
        map.put("083", "ClassificationDdc");
        map.put("084", "Classification");
        map.put("085", "ClassificationDdc");
        map.put("086", "Classification");
        return Map.copyOf(map);
    }

    private static Map<String, String> linkingProperties() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("760", "series");
        map.put("762", "subseries");
        map.put("765", "translationOf");
        map.put("767", "hasTranslation");
        map.put("770", "supplement");
        map.put("772", "supplementTo");
        map.put("773", "partOf");
        map.put("774", "hasPart");
        map.put("775", "otherEdition");
        map.put("776", "otherPhysicalFormat");
        map.put("777", "issuedWith");
        map.put("780", "precededBy");
        map.put("785", "succeededBy");
        map.put("786", "dataSource");
        map.put("787", "relatedTo");
        return Map.copyOf(map);
    }

    private static Map<String, String> noteProperties() {
        Map<String, String> map = new HashMap<>();
        map.put("505", "tableOfContents");
        map.put("508", "credits");
        map.put("511", "performer");
        map.put("518", "eventContent");
        map.put("522", "geographicCoverage");
        map.put("524", "preferredCitation");
        map.put("525", "supplementaryContent");
        map.put("535", "originalVersion");
        map.put("545", "biographicalData");
        map.put("555", "index");
        map.put("580", "relationshipInformation");
        map.put("581", "relatedTo");
        map.put("588", "sourceConsulted");
        return Map.copyOf(map);
    }
}
