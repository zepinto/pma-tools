//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts.browser;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.mp.SystemPositionAndAttitude;
import pt.omst.neptus.mra.LogMarker;
import pt.omst.neptus.mra.api.CorrectedPosition;
import pt.omst.neptus.mra.api.SidescanParser;
import pt.omst.neptus.mra.api.SidescanParserFactory;
import pt.omst.neptus.mra.importers.IMraLogGroup;
import pt.omst.neptus.util.llf.LsfLogSource;
import pt.omst.neptus.plugins.mra.exporters.indexraster.JsonContactUtils;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

@Slf4j
public class LogMarkerImporter {

    public static CompressedContact importMark(LogMarker marker, IMraLogGroup source, CorrectedPosition cp, SidescanParser sssParser) {
        String label = marker.getLabel();
        String filename = JsonContactUtils.sanitize(label);
        File outDir = new File(source.getFile("contacts"), filename);
        outDir.mkdirs();
        SystemPositionAndAttitude pose = cp.getPosition(marker.getTimestamp() / 1000.0);        
        try {
            Contact contact = JsonContactUtils.convert(marker, source, pose, sssParser, outDir);
            Files.write(new File(outDir, "contact.json").toPath(),
            Converter.ContactToJsonString(contact).getBytes());
            JsonContactUtils.zipFolderAndDelete(outDir);
            return new CompressedContact(new File(outDir.getParent(), label + ".zct"));
        } catch (Exception e) {
            log.error("Error importing marker: " + e.getMessage());
        }
        return null;
    }

    public static void importMarks(File folder) {
        // load zipped contacts
        ContactCollection collection = ContactCollection.fromFolder(folder);

        List<CompressedContact> modernContacts = collection.getAllContacts();
        LinkedHashMap<String, CompressedContact> contactMap = new LinkedHashMap<>();
        for (CompressedContact contact : modernContacts) {
            contactMap.put(contact.getLabel(), contact);
        }

        // load old markers and transform to new format
        Collection<LogMarker> oldMarkers = LogMarker.load(folder);
        log.info("Importing " + oldMarkers.size() + " markers from " + folder.getAbsolutePath());
        ArrayList<String> missingContacts = new ArrayList<>();

        for (LogMarker marker : oldMarkers) {
            CompressedContact contact = contactMap.get(marker.getLabel());
            if (contact == null)
                missingContacts.add(marker.getLabel());
        }
            int count = 0;
                
        if (missingContacts.size() > 0) {
            try {
                File output = new File(folder, "contacts");
                LsfLogSource source = new LsfLogSource(new File(folder, "Data.lsf"), null);
                CorrectedPosition cp = new CorrectedPosition(source);
                SidescanParser sssParser = SidescanParserFactory.build(source);
                for (String label : missingContacts) {
                    LogMarker marker = oldMarkers.stream().filter(m -> m.getLabel().equals(label)).findFirst()
                            .orElse(null);
                    if (marker != null) {
                        String filename = JsonContactUtils.sanitize(label);
                        File outDir = new File(output, filename);
                        outDir.mkdirs();
                        SystemPositionAndAttitude pose = cp.getPosition(marker.getTimestamp() / 1000.0);
                        Contact contact = JsonContactUtils.convert(marker, source, pose, sssParser, outDir);
                        Files.write(new File(outDir, "contact.json").toPath(),
                                Converter.ContactToJsonString(contact).getBytes());
                        JsonContactUtils.zipFolderAndDelete(outDir);
                        count++;
                    }
                }
            } catch (Exception e) {
                log.error("Error importing markers: " + e.getMessage());
            }
            log.info("Modernized " + count + " contacts");
        } else {
            log.info("All contacts found");
        }
    }

    public static void main(String[] args) {
        File folder = new File("/home/zp/workspace/neptus/log/downloaded/lauv-venus/20250423/141908_TITA-1/");
        importMarks(folder);
    }
}
