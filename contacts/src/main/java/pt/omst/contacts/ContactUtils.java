//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.WGS84Utilities;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.core.SystemPositionAndAttitude;
import pt.lsts.neptus.mra.LogMarker;
import pt.lsts.neptus.mra.SidescanLogMarker;
import pt.lsts.neptus.util.StreamUtil;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterCreator;
import pt.omst.rasterlib.IndexedRasterUtils.RasterContactInfo;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.SensorInfo;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.sidescan.ISidescanLine;
import pt.omst.sidescan.SidescanHistogramNormalizer;
import pt.omst.sidescan.SidescanParser;
import pt.omst.sidescan.SidescanParserFactory;

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
                raster.getSamples().sort((o1, o2) -> o1.getTimestamp().compareTo(o2.getTimestamp()));
                sensorInfo = raster.getSensorInfo();
                info.setMinRange(sensorInfo.getMinRange());
                info.setMaxRange(sensorInfo.getMaxRange());

                info.setStartTimeStamp(raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli());
                info.setEndTimeStamp(raster.getSamples().getLast().getTimestamp().toInstant().toEpochMilli());

                if (boxAnnotation != null) {
                    double newMinRange = info.getMinRange()
                            + boxAnnotation.getNormalizedX() * (info.getMaxRange() - info.getMinRange());
                    double newMaxRange = info.getMinRange()
                            + boxAnnotation.getNormalizedX2() * (info.getMaxRange() - info.getMinRange());
                    double newStartTimestamp = info.getStartTimeStamp()
                            + boxAnnotation.getNormalizedY() * (info.getEndTimeStamp() - info.getStartTimeStamp());
                    double newEndTimestamp = info.getStartTimeStamp()
                            + boxAnnotation.getNormalizedY2() * (info.getEndTimeStamp() - info.getStartTimeStamp());
                    info.setBoxAnnotation(boxAnnotation);
                    info.setMinRange(newMinRange);
                    info.setMaxRange(newMaxRange);
                    info.setStartTimeStamp((long) newStartTimestamp);
                    info.setEndTimeStamp((long) newEndTimestamp);
                }
            } catch (IOException e) {
                log.error("Error reading raster file: " + e.getMessage());
            }
        } else {
            info.setMinRange(info.getMaxRange());
            info.setStartTimeStamp(info.getEndTimeStamp());
        }
        return info;
    }

    public static Contact convert(LogMarker marker, SidescanParser parser, SidescanHistogramNormalizer normalizer,
            File outputFolder) throws Exception {
        Collection<ISidescanLine> lines = parser.getLinesAtTime((long) marker.getTimestamp());
        SystemPositionAndAttitude pose = null;
        if (lines.isEmpty()) {
            log.warn("No sidescan lines found for marker at time " + marker.getTimestamp());
        }
        ISidescanLine line = lines.iterator().next();
        pose = line.getState();
        return convert(marker, pose, parser, normalizer, outputFolder);
    }

    public static Collection<Contact> convertContacts(File folder) {
        if (folder == null) {
            return null;
        }
        if (folder.isFile() && folder.getName().equals("marks.dat")) {
            folder = folder.getParentFile();
        } else if (folder.isDirectory() && folder.getName().equals("contacts")) {
            folder = folder.getParentFile();
        }
        Collection<LogMarker> markers = LogMarker.load(folder);
        SidescanParser parser = SidescanParserFactory.build(folder);
        ArrayList<Contact> contacts = new ArrayList<>();
        SidescanHistogramNormalizer normalizer = SidescanHistogramNormalizer.create(parser, folder);
        if (markers.isEmpty()) {
            log.info("No markers found in folder " + folder.getAbsolutePath());
            return new ArrayList<>();
        }

        int count = 0;
        File output = new File(folder, "contacts");
        output.mkdirs();
        for (LogMarker marker : markers) {
            String label = marker.getLabel();
            if (label == null || label.isEmpty())
                label = "contact_" + count;
            String filename = sanitize(label);
            File outDir = new File(output, filename);
            outDir.mkdirs();
            try {
                Contact contact = convert(marker, parser, normalizer, outDir);
                contacts.add(contact);
                count++;
                Files.write(new File(outDir, "contact.json").toPath(),
                        Converter.ContactToJsonString(contact).getBytes());
            } catch (Exception e) {
                log.error("Error converting marker " + label + ": " + e.getMessage());
            }
            zipFolderAndDelete(outDir);
        }
        return contacts;
    }

    /**
     * Convert a log marker to a contact
     * 
     * @param marker       The marker to convert
     * @param log          The log group to get the system name from
     * @param pose         The pose of the vehicle at the time of the marker
     * @param parser       The parser to use to get the raster
     * @param outputFolder The folder to save the raster to
     * @return The contact for the given marker
     * @throws Exception If an error occurs while converting the marker
     */
    public static Contact convert(LogMarker marker, SystemPositionAndAttitude pose, SidescanParser parser,
            SidescanHistogramNormalizer normalizer,
            File outputFolder) throws Exception {
        if (marker.getParent() != null)
            return null;
        Contact contact = new Contact();
        contact.setLabel(marker.getLabel());
        contact.setLatitude(Math.toDegrees(marker.getLatRads()));
        contact.setLongitude(Math.toDegrees(marker.getLonRads()));
        contact.setObservations(new ArrayList<>());
        if (pose != null)
            contact.setDepth(pose.getDepth() + pose.getAltitude());
        ArrayList<LogMarker> thisAndChildren = new ArrayList<>();
        thisAndChildren.add(marker);
        thisAndChildren.addAll(marker.getChildren());
        for (LogMarker child : thisAndChildren) {
            if (child instanceof SidescanLogMarker)
                contact.getObservations()
                        .addAll(getSidescanObservations((SidescanLogMarker) child, pose, parser, normalizer, outputFolder));
            else
                contact.getObservations().addAll(getGenericObservations(child, pose));
        }

        return contact;
    }

    public static String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    public static void zipFolderAndDelete(File folder) {
        try {
            File zipFile = new File(folder.getParentFile(), folder.getName() + ".zct");
            ZipUtils.zipDir(zipFile.getAbsolutePath(), folder.getAbsolutePath());
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            folder.delete();
        } catch (Exception e) {
            log.error("Error zipping folder", e);
        }
    }

    /**
     * Get the observation for a sidescan marker and subsystem
     * 
     * @param marker       The sidescan marker to get the observation for
     * @param pose         The pose of the vehicle at the time of the marker
     * @param log          The log group, used to get the system name
     * @param parser       The parser to use to get the raster
     * @param subsystem    The subsystem to get the observation for
     * @param outputFolder The folder to save the raster to
     * @return The observation for the given marker and subsystem
     */
    public static Observation getSidescanObservation(SidescanLogMarker marker, SystemPositionAndAttitude pose,
            SidescanParser parser, SidescanHistogramNormalizer normalizer, int subsystem, File outputFolder) {
        SensorInfo info = new SensorInfo();
        File rasterFile = new File(outputFolder, "marker_" + (long) marker.getTimestamp() + "_" + subsystem + ".json");
        IndexedRasterCreator creator = new IndexedRasterCreator(
                rasterFile,
                info);
        int margin = 50;
        creator.export(marker, parser, normalizer, subsystem, margin);
        BufferedImage exported = creator.getImage();

        double boxX = margin, boxY = margin, boxWidth = exported.getWidth() - margin * 2,
                boxHeight = exported.getHeight() - margin * 2;
        boxX /= exported.getWidth();
        boxY /= exported.getHeight();
        boxWidth /= exported.getWidth();
        boxHeight /= exported.getHeight();

        Annotation boxSize = new Annotation();
        boxSize.setAnnotationType(AnnotationType.MEASUREMENT);
        boxSize.setMeasurementType(MeasurementType.BOX);
        boxSize.setNormalizedX(boxX);
        boxSize.setNormalizedY(boxY);
        boxSize.setNormalizedX2(boxX + boxWidth);
        boxSize.setNormalizedY2(boxY + boxHeight);
        boxSize.setTimestamp(OffsetDateTime.ofInstant(marker.getDate().toInstant(), ZoneId.systemDefault()));
        boxSize.setUserName(pt.omst.util.UserPreferences.getUsername());

        log.debug("Box is at " + boxX + ", " + boxY + " with width " + boxWidth + " and height " + boxHeight);

        // Add the observation for the marker itself
        Observation observation = new Observation();
        observation.setDepth(pose.getPosition().getDepth());
        observation.setLatitude(Math.toDegrees(marker.getLatRads()));
        observation.setLongitude(Math.toDegrees(marker.getLonRads()));
        observation.setTimestamp(OffsetDateTime.ofInstant(marker.getDate().toInstant(), ZoneId.systemDefault()));
        observation.setUserName(System.getProperty("user.name"));
        observation.setSystemName("rasterfall");
        observation.setRasterFilename(rasterFile.getName());
        observation.setAnnotations(new ArrayList<>());
        observation.getAnnotations().add(boxSize);
        return observation;
    }

    /**
     * Get the horizontal pixels per meter for a raster and image
     * 
     * @param raster The raster to get the pixels per meter for
     * @param image  The image to get the pixels per meter for
     * @return The horizontal pixels per meter for the raster and image
     */
    public static double getHorizontalPixelsPerMeter(IndexedRaster raster, BufferedImage image) {
        double pixelWidth = image.getWidth();
        double rasterWidth = raster.getSensorInfo().getMaxRange() - raster.getSensorInfo().getMinRange();
        return pixelWidth / rasterWidth;
    }

    /**
     * Get the vertical pixels per meter for a raster and image based on the first
     * and last samples.
     * Assumes the vehicle is moving in a straight line between the first and last
     * samples.
     * 
     * @param raster The raster to get the pixels per meter for
     * @param image  The image to get the pixels per meter for
     * @return The vertical pixels per meter for the raster and image, based on the
     *         first and last sample positions
     */
    public static double getVerticalPixelsPerMeter(IndexedRaster raster, BufferedImage image) {
        double pixelHeight = image.getHeight();
        double verticalDistance = WGS84Utilities.distance(
                raster.getSamples().getFirst().getPose().getLatitude(),
                raster.getSamples().getFirst().getPose().getLongitude(),
                raster.getSamples().getLast().getPose().getLatitude(),
                raster.getSamples().getLast().getPose().getLongitude());
        return pixelHeight / verticalDistance;
    }

    /**
     * Get the observations for a sidescan marker
     * 
     * @param marker       The sidescan marker to get the observations for
     * @param pose         The pose of the vehicle at the time of the marker
     * @param log          The log group, used to get the system name
     * @param parser       The parser to use to get the raster
     * @param outputFolder The folder to save the raster to
     * @return The observations for the given marker
     * @throws Exception If an error occurs while getting the observations
     */
    public static ArrayList<Observation> getSidescanObservations(SidescanLogMarker marker,
            SystemPositionAndAttitude pose, SidescanParser parser, SidescanHistogramNormalizer normalizer,
            File outputFolder) throws Exception {
        ArrayList<Observation> observations = new ArrayList<>();
        ArrayList<Integer> subsystems = parser.getSubsystemList();
        for (int subsystem : subsystems) {
            observations.add(getSidescanObservation(marker, pose, parser, normalizer, subsystem, outputFolder));
        }

        return observations;
    }

    /**
     * Get the observations for a generic marker
     * 
     * @param marker The marker to get the observations for
     * @param pose   The pose of the vehicle at the time of the marker
     * @param log    The log group, used to get the system name
     * @return The observations for the given marker
     */
    public static ArrayList<Observation> getGenericObservations(LogMarker marker, SystemPositionAndAttitude pose) {
        ArrayList<Observation> observations = new ArrayList<>();
        Observation observation = new Observation();
        observation.setDepth(pose.getPosition().getDepth());
        observation.setLatitude(Math.toDegrees(marker.getLatRads()));
        observation.setLongitude(Math.toDegrees(marker.getLonRads()));
        observation.setTimestamp(OffsetDateTime.ofInstant(marker.getDate().toInstant(), ZoneId.systemDefault()));
        observation.setUserName(System.getProperty("user.name"));
        observation.setSystemName("rasterfall");
        observations.add(observation);

        return observations;
    }

    public static void main(String[] args) {
        // marine sonics
        File folder = new File("/home/zp/Desktop/data-samples/153320_03-fat-sidescan-depth/");
        Collection<Contact> contacts = convertContacts(folder);
        log.info("Converted " + contacts.size() + " contacts.");
    }
}
