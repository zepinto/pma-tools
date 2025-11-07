// To use this code, add the following Maven dependency to your project:
//
//     org.projectlombok : lombok : 1.18.2
//     com.fasterxml.jackson.core     : jackson-databind          : 2.9.0
//     com.fasterxml.jackson.datatype : jackson-datatype-jsr310   : 2.9.0
//
// Import this package:
//
//     import pt.omst.rasterlib.Converter;
//
// Then you can deserialize a JSON string with
//
//     Annotation data = Converter.AnnotationFromJsonString(jsonString);
//     Contact data = Converter.ContactFromJsonString(jsonString);
//     Observation data = Converter.ObservationFromJsonString(jsonString);
//     IndexedRaster data = Converter.IndexedRasterFromJsonString(jsonString);
//     SampleDescription data = Converter.SampleDescriptionFromJsonString(jsonString);
//     SensorInfo data = Converter.SensorInfoFromJsonString(jsonString);

package pt.omst.rasterlib;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class Converter {
    // Date-time helpers

    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ISO_DATE_TIME)
            .appendOptional(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .appendOptional(DateTimeFormatter.ISO_INSTANT)
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SX"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .toFormatter()
            .withZone(ZoneOffset.UTC);

    public static OffsetDateTime parseDateTimeString(String str) {
        return ZonedDateTime.from(Converter.DATE_TIME_FORMATTER.parse(str)).toOffsetDateTime();
    }

    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ISO_TIME)
            .appendOptional(DateTimeFormatter.ISO_OFFSET_TIME)
            .parseDefaulting(ChronoField.YEAR, 2020)
            .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
            .toFormatter()
            .withZone(ZoneOffset.UTC);

    public static OffsetTime parseTimeString(String str) {
        return ZonedDateTime.from(Converter.TIME_FORMATTER.parse(str)).toOffsetDateTime().toOffsetTime();
    }
    // Serialize/deserialize helpers

    public static Annotation AnnotationFromJsonString(String json) throws IOException {
        return getAnnotationObjectReader().readValue(json);
    }

    public static String AnnotationToJsonString(Annotation obj) throws JsonProcessingException {
        return getAnnotationObjectWriter().writeValueAsString(obj);
    }

    public static Contact ContactFromJsonString(String json) throws IOException {
        return getContactObjectReader().readValue(json);
    }

    public static String ContactToJsonString(Contact obj) throws JsonProcessingException {
        return getContactObjectWriter().writeValueAsString(obj);
    }

    public static Observation ObservationFromJsonString(String json) throws IOException {
        return getObservationObjectReader().readValue(json);
    }

    public static String ObservationToJsonString(Observation obj) throws JsonProcessingException {
        return getObservationObjectWriter().writeValueAsString(obj);
    }

    public static IndexedRaster IndexedRasterFromJsonString(String json) throws IOException {
        return getIndexedRasterObjectReader().readValue(json);
    }

    public static String IndexedRasterToJsonString(IndexedRaster obj) throws JsonProcessingException {
        return getIndexedRasterObjectWriter().writeValueAsString(obj);
    }

    public static SampleDescription SampleDescriptionFromJsonString(String json) throws IOException {
        return getSampleDescriptionObjectReader().readValue(json);
    }

    public static String SampleDescriptionToJsonString(SampleDescription obj) throws JsonProcessingException {
        return getSampleDescriptionObjectWriter().writeValueAsString(obj);
    }

    public static SensorInfo SensorInfoFromJsonString(String json) throws IOException {
        return getSensorInfoObjectReader().readValue(json);
    }

    public static String SensorInfoToJsonString(SensorInfo obj) throws JsonProcessingException {
        return getSensorInfoObjectWriter().writeValueAsString(obj);
    }

    private static ObjectReader AnnotationReader;
    private static ObjectWriter AnnotationWriter;

    private static void instantiateAnnotationMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                String value = jsonParser.getText();
                return Converter.parseDateTimeString(value);
            }
        });
        mapper.registerModule(module);
        AnnotationReader = mapper.readerFor(Annotation.class);
        AnnotationWriter = mapper.writerFor(Annotation.class);
    }

    private static ObjectReader getAnnotationObjectReader() {
        if (AnnotationReader == null) instantiateAnnotationMapper();
        return AnnotationReader;
    }

    private static ObjectWriter getAnnotationObjectWriter() {
        if (AnnotationWriter == null) instantiateAnnotationMapper();
        return AnnotationWriter;
    }

    private static ObjectReader ContactReader;
    private static ObjectWriter ContactWriter;

    private static void instantiateContactMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                String value = jsonParser.getText();
                return Converter.parseDateTimeString(value);
            }
        });
        mapper.registerModule(module);
        ContactReader = mapper.readerFor(Contact.class);
        ContactWriter = mapper.writerFor(Contact.class);
    }

    private static ObjectReader getContactObjectReader() {
        if (ContactReader == null) instantiateContactMapper();
        return ContactReader;
    }

    private static ObjectWriter getContactObjectWriter() {
        if (ContactWriter == null) instantiateContactMapper();
        return ContactWriter;
    }

    private static ObjectReader ObservationReader;
    private static ObjectWriter ObservationWriter;

    private static void instantiateObservationMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                String value = jsonParser.getText();
                return Converter.parseDateTimeString(value);
            }
        });
        mapper.registerModule(module);
        ObservationReader = mapper.readerFor(Observation.class);
        ObservationWriter = mapper.writerFor(Observation.class);
    }

    private static ObjectReader getObservationObjectReader() {
        if (ObservationReader == null) instantiateObservationMapper();
        return ObservationReader;
    }

    private static ObjectWriter getObservationObjectWriter() {
        if (ObservationWriter == null) instantiateObservationMapper();
        return ObservationWriter;
    }

    private static ObjectReader IndexedRasterReader;
    private static ObjectWriter IndexedRasterWriter;

    private static void instantiateIndexedRasterMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                String value = jsonParser.getText();
                return Converter.parseDateTimeString(value);
            }
        });
        mapper.registerModule(module);
        IndexedRasterReader = mapper.readerFor(IndexedRaster.class);
        IndexedRasterWriter = mapper.writerFor(IndexedRaster.class);
    }

    private static ObjectReader getIndexedRasterObjectReader() {
        if (IndexedRasterReader == null) instantiateIndexedRasterMapper();
        return IndexedRasterReader;
    }

    private static ObjectWriter getIndexedRasterObjectWriter() {
        if (IndexedRasterWriter == null) instantiateIndexedRasterMapper();
        return IndexedRasterWriter;
    }

    private static ObjectReader SampleDescriptionReader;
    private static ObjectWriter SampleDescriptionWriter;

    private static void instantiateSampleDescriptionMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                String value = jsonParser.getText();
                return Converter.parseDateTimeString(value);
            }
        });
        mapper.registerModule(module);
        SampleDescriptionReader = mapper.readerFor(SampleDescription.class);
        SampleDescriptionWriter = mapper.writerFor(SampleDescription.class);
    }

    private static ObjectReader getSampleDescriptionObjectReader() {
        if (SampleDescriptionReader == null) instantiateSampleDescriptionMapper();
        return SampleDescriptionReader;
    }

    private static ObjectWriter getSampleDescriptionObjectWriter() {
        if (SampleDescriptionWriter == null) instantiateSampleDescriptionMapper();
        return SampleDescriptionWriter;
    }

    private static ObjectReader SensorInfoReader;
    private static ObjectWriter SensorInfoWriter;

    private static void instantiateSensorInfoMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                String value = jsonParser.getText();
                return Converter.parseDateTimeString(value);
            }
        });
        mapper.registerModule(module);
        SensorInfoReader = mapper.readerFor(SensorInfo.class);
        SensorInfoWriter = mapper.writerFor(SensorInfo.class);
    }

    private static ObjectReader getSensorInfoObjectReader() {
        if (SensorInfoReader == null) instantiateSensorInfoMapper();
        return SensorInfoReader;
    }

    private static ObjectWriter getSensorInfoObjectWriter() {
        if (SensorInfoWriter == null) instantiateSensorInfoMapper();
        return SensorInfoWriter;
    }
}
