//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Optional;

import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.util.StreamUtil;
import pt.omst.neptus.util.ZipUtils;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils.RasterContactInfo;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.SensorInfo;
import pt.omst.rasterlib.contacts.CompressedContact;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContactUtils {
    public static RasterContactInfo getContactInfo(CompressedContact contact) {
        Contact c = contact.getContact();
        RasterContactInfo info = new RasterContactInfo();
        info.setCenter(new LocationType(c.getLatitude(), c.getLongitude()));
        info.setLabel(c.getLabel());

        Observation sssObservation = null;
        Annotation boxAnnotation = null;
        SensorInfo sensorInfo = null;
        IndexedRaster raster = null;

        Optional<Observation> optionalObs = contact.getContact().getObservations().stream()
                .filter(obs -> obs.getRasterFilename() != null)
                .findFirst();
        if (optionalObs.isPresent()) {
            sssObservation = optionalObs.get();
            Optional<Annotation> classificationOptional = sssObservation.getAnnotations().stream()
                    .filter(ann -> ann.getAnnotationType() == AnnotationType.CLASSIFICATION)
                    .findFirst();
            if (classificationOptional.isPresent()) {
                info.setClassification(classificationOptional.get());
            }
            Optional<Annotation> boxOptional = sssObservation.getAnnotations().stream()
                    .filter(ann -> ann.getAnnotationType() == AnnotationType.MEASUREMENT
                            && ann.getMeasurementType() == MeasurementType.BOX)
                    .findFirst();
            if (boxOptional.isPresent()) {
                boxAnnotation = boxOptional.get();
            }
            try {
                InputStream is = ZipUtils.getFileInZip(contact.getZctFile().getAbsolutePath(),
                        sssObservation.getRasterFilename());
                String rasterJson = StreamUtil.copyStreamToString(is);
                raster = Converter.IndexedRasterFromJsonString(rasterJson);
                raster.getSamples().sort( (o1, o2) -> o1.getTimestamp().compareTo(o2.getTimestamp()));
                sensorInfo = raster.getSensorInfo();
                info.setMinRange(sensorInfo.getMinRange());
                info.setMaxRange(sensorInfo.getMaxRange());

                info.setStartTimeStamp(raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli());
                info.setEndTimeStamp(raster.getSamples().getLast().getTimestamp().toInstant().toEpochMilli());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                //System.out.println(info.label+" is from "+sdf.format(new Date(info.startTimeStamp))+" to "+sdf.format(new Date(info.endTimeStamp)));
                if (boxAnnotation != null) {
                    double newMinRange = info.getMinRange() + boxAnnotation.getNormalizedX() * (info.getMaxRange() - info.getMinRange());
                    double newMaxRange = info.getMinRange() + boxAnnotation.getNormalizedX2() * (info.getMaxRange() - info.getMinRange());
                    double newStartTimestamp = info.getStartTimeStamp() + boxAnnotation.getNormalizedY() * (info.getEndTimeStamp() - info.getStartTimeStamp());
                    double newEndTimestamp = info.getStartTimeStamp() + boxAnnotation.getNormalizedY2() * (info.getEndTimeStamp() - info.getStartTimeStamp());
                    info.setBoxAnnotation(boxAnnotation);
                    info.setMinRange(newMinRange);
                    info.setMaxRange(newMaxRange);
                    info.setStartTimeStamp((long) newStartTimestamp);
                    info.setEndTimeStamp((long) newEndTimestamp);
                }
            }
            catch (IOException e) {
                log.error("Error reading raster file: " + e.getMessage());
            }
        } else {
            info.setMinRange(info.getMaxRange());
            info.setStartTimeStamp(info.getEndTimeStamp());
        }
        return info;
    }
}
