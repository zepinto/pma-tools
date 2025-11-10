//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.neptus.sidescan;

import java.io.File;
import java.util.Collection;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import pt.omst.neptus.sidescan.jsf.JsfSidescanParser;
import pt.omst.neptus.sidescan.sdf.SdfSidescanParser;
import pt.omst.neptus.sidescan.sds.SdsParser;

public class SidescanParserFactory {
    
    public static SidescanParser build(File logFolder) {
        return build(logFolder, null);
    }

    public static SidescanParser build(File logFolder, Consumer<String> progress) {
        if (logFolder == null) {
            return null;
        }
        
        File[] jsfFiles = getFilesWithExtension(logFolder, "jsf");
        if (jsfFiles.length > 0) {
            return new JsfSidescanParser(jsfFiles, progress);
        }

        // SDF.
        File[] sdfFiles = getFilesWithExtension(logFolder, "sdf");
        if (sdfFiles.length > 0) {
            return new SdfSidescanParser(sdfFiles, progress);        
        }

        File[] sdsFiles = getFilesWithExtension(logFolder, "sds");
        if (sdsFiles.length > 0) {
            SdsParser parser = new SdsParser();
            try {
                for (File sdsFile : sdsFiles) {
                    parser.parse(sdsFile);
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Error parsing SDS files.", e);
            }
            
            return parser;
        }
        // If no specific parser found, but we might create an ImcSidescanParser later,
        // don't cache null here, let getParser handle ImcSidescanParser caching.
        return null;
    }


    private static File[] getFilesWithExtension(final File root, final String extension) {
        Collection<File> files = FileUtils.listFiles(root, new WildcardFileFilter("*." + extension, IOCase.INSENSITIVE), null);
        return files.toArray(new File[0]);
    }
}
