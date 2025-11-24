//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Paulo Dias
 * 1/Mar/2005
 */

package pt.lsts.neptus.util;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.UnicodeExtraFieldPolicy;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author Paulo Dias
 */
public class ZipUtils {

    /** Log handle. */
    private final static Logger LOG = LoggerFactory.getLogger(ZipUtils.class);

    /** To avoid instantiation */
    public ZipUtils() {
    }

    /**
     * Unzips a Zip file into destination path.
     * @param zipFile The Zip file to unzip.
     * @param outputDir The destination path.
     * @throws IOException If an error occurs while unzipping.
     */
    public static void unzip(String zipFile, Path outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = outputDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    // Ensure parent directories exist
                    Files.createDirectories(filePath.getParent());

                    // Write file contents
                    try (OutputStream os = Files.newOutputStream(filePath)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Update a file inside a Zip file.
     * @param zipFilePath The path to the Zip file.
     * @param fileNameInZip The name of the file to update inside the Zip file.
     * @param newContent The new content to write to the file.
     * @throws IOException If an error occurs while updating the file.
     */
    public static void updateFileInZip(String zipFilePath, String fileNameInZip, String newContent) throws IOException {
        Path tempZip = Files.createTempFile("tempZip", ".zip");

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip.toFile()))) {

            ZipEntry entry;
            boolean fileUpdated = false;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                zos.putNextEntry(new ZipEntry(entryName));

                if (entryName.equals(fileNameInZip)) {
                    // write the new content
                    zos.write(newContent.getBytes());
                    fileUpdated = true;
                } else {
                    // write the original content
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = zis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                zos.closeEntry();
                zis.closeEntry();
            }

            if (!fileUpdated) {
                // The file to update was not found in the ZIP file, so add it
                ZipEntry newEntry = new ZipEntry(fileNameInZip);
                zos.putNextEntry(newEntry);
                zos.write(newContent.getBytes());
                zos.closeEntry();
            }
        }

        // Replace the original ZIP file with the updated one
        Files.move(tempZip, Paths.get(zipFilePath), StandardCopyOption.REPLACE_EXISTING);
    }

    public static InputStream getFileInZip(String zipFilePath, String fileNameInZip) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().equals(fileNameInZip)) {
                return zis;
            }
        }
        return null;
    }



    /**
     * Unzips a Zip file into destination path.
     * It assumes ibm437 encoding at first.
     */
    public static boolean unZip(String zipFile, String destinationPath) {
        ZipFile fxZipFile = null;
        try {
            if (Charset.isSupported("ibm437"))
                fxZipFile = new ZipFile(zipFile, "ibm437");
            else
                fxZipFile = new ZipFile(zipFile);

            LOG.debug(zipFile + "   " + fxZipFile.getEncoding());

            Enumeration<ZipArchiveEntry> entries = fxZipFile.getEntries();

            File destination = new File(destinationPath).getAbsoluteFile();
            destination.mkdirs();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                InputStream content = fxZipFile.getInputStream(entry);

                try {
                    LOG.debug(entry.getName() + "   " + entry.getLastModifiedDate() + "\n"
                            + Arrays.toString(entry.getExtraFields()));
                    if (entry.isDirectory()) {
                        File dir = new File(destinationPath, entry.getName());
                        boolean bl = dir.mkdirs();
                        LOG.debug("Created dir (" + bl + "): " + dir.getAbsolutePath());
                    } else {
                        File file = new File(destinationPath, entry.getName());
                        file.getParentFile().mkdirs();
                        FileOutputStream fxOutStream = new FileOutputStream(file);
                        boolean bl = StreamUtil.copyStreamToStream(content, fxOutStream);
                        try {
                            fxOutStream.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        file.setLastModified(entry.getTime() < 0 ? System.currentTimeMillis() : entry.getTime());
                        LOG.debug("Created file(" + bl + "): " + entry.getName());
                    }
                } finally {
                    try {
                        content.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            fxZipFile.close();

            return true;
        } catch (Exception e) {
            LOG.error("unZip", e);
            return false;
        } finally {
            if (fxZipFile != null) {
                try {
                    fxZipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Compresses a source folder (of file) into a Zip.
     */
    public static boolean zipDir(String zipFile, String sourceDir, String encoding) {
        File fxZipFile = new File(zipFile);
        try (ZipArchiveOutputStream zOutStream = new ZipArchiveOutputStream(fxZipFile)) {
            zOutStream.setEncoding(encoding);

            zOutStream.setUseLanguageEncodingFlag(true);
            zOutStream.setFallbackToUTF8(false);
            zOutStream.setCreateUnicodeExtraFields(UnicodeExtraFieldPolicy.NOT_ENCODEABLE);

            zipDirWorker(sourceDir, sourceDir, zOutStream);

            return true;
        } catch (Exception e) {
            LOG.error("zipDir", e);
            return false;
        }
    }

    /**
     * Compresses a source folder (of file) into a Zip
     * (with "IBM437" encoding, the most usual for Zip files).
     */
    public static boolean zipDir(String zipFile, String sourceDir) {
        if (Charset.isSupported("ibm437"))
            return zipDir(zipFile, sourceDir, "ibm437");
        else
            return zipDir(zipFile, sourceDir, null);
    }

    /**
     * This is the Zip worker.
     */
    private static void zipDirWorker(String dir2zip, String baseDir, ZipArchiveOutputStream zOutStream) {
        File baseDirFile = new File(baseDir);
        if (baseDirFile.isFile()) {
            baseDirFile = baseDirFile.getAbsoluteFile().getParentFile();
            baseDir = baseDirFile.getAbsolutePath();
        }
        File zipDir = new File(dir2zip).getAbsoluteFile();
        // get a listing of the directory content
        String[] dirList;
        if (zipDir.isDirectory()) {
            dirList = zipDir.list();
        } else {
            dirList = new String[]{zipDir.getName()};
            zipDir = zipDir.getParentFile();
        }
        // loop through dirList, and zip the files
        for (String aDirList : dirList) {
            File f = new File(zipDir, aDirList);
            if (f.isDirectory()) {
                // if the File object is a directory, call this
                // function again to add its content recursively
                String filePath = f.getPath();
                zipDirWorker(filePath, baseDir, zOutStream);
                // loop again
                continue;
            }
            // if we reached here, the File object was not a directory
            addZipEntry(relativizeFilePath(baseDir, f.getPath()), f.getPath(), zOutStream);
        }
    }
    
    /**
     * Relativize a file path against a base directory.
     * @param baseDir Base directory path
     * @param filePath File path to relativize
     * @return Relative path
     */
    private static String relativizeFilePath(String baseDir, String filePath) {
        File base = new File(baseDir);
        File file = new File(filePath);
        return base.toURI().relativize(file.toURI()).getPath();
    }

    /**
     * Called to add a Zip entry.
     */
    private static boolean addZipEntry(String entryName, String filePath, ZipArchiveOutputStream zOutStream) {
        try {
            entryName = entryName.replace('\\', '/');
            File contentFx = new File(filePath);

            ZipArchiveEntry entry = (ZipArchiveEntry) zOutStream.createArchiveEntry(contentFx, entryName);

            zOutStream.putArchiveEntry(entry);

            FileInputStream fxInStream = new FileInputStream(contentFx);
            OutputStream os = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    zOutStream.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    zOutStream.write(b, off, len);
                }
            };
            boolean bl = StreamUtil.copyStreamToStream(fxInStream, os);
            zOutStream.flush();
            zOutStream.closeArchiveEntry();

            try {
                fxInStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return bl;
        } catch (IOException e) {
            LOG.error("addZipEntry", e);
            return false;
        }
    }

    /**
     * Searches a Zip mission file for the mission file and returns it as an {@link InputStream}-
     *
     * @param zipFile
     * @return
     */
    public static InputStream getMissionZipedAsInputSteam(String zipFile) {
        boolean missionFileFound = false;
        try {
            FileInputStream fxInStream = new FileInputStream(zipFile);
            ZipArchiveInputStream zInStream;
            if (Charset.isSupported("ibm437"))
                zInStream = new ZipArchiveInputStream(fxInStream, "ibm437");
            else
                zInStream = new ZipArchiveInputStream(fxInStream);

            while (true) {
                ZipArchiveEntry zipEntry = zInStream.getNextZipEntry();

                if (zipEntry == null)
                    break;
                if (!zipEntry.isDirectory()) {
                    String fileZname = zipEntry.getName();
                    if (fileZname.equalsIgnoreCase("mission.nmis")) {
                        missionFileFound = true;
                        break;
                    }
                }
            }

            if (missionFileFound)
                return zInStream;
            else
                return null;
        } catch (Exception e) {
            LOG.error("unZip", e);
            return null;
        }
    }
}
