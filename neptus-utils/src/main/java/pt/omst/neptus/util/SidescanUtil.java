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
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
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
 * Author: pdias
 * 15/12/2015
 */

package pt.omst.neptus.util;

import java.awt.image.BufferedImage;

import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.sidescan.ISidescanLine;
import pt.omst.neptus.sidescan.SidescanLine;
import pt.omst.neptus.sidescan.SidescanParameters;
import pt.omst.neptus.sidescan.SidescanPoint;

/**
 * This class holds utility methods for sidescan
 *
 * @author Paulo Dias
 */
public class SidescanUtil {

    /** Avoid instantiation */
    private SidescanUtil() {
    }

    /**
     * Method to convert from sidescan x point to mouse click x point in the image.
     *
     * @param sidescanLineX The x index of the sidescan (middle is half of the data size)
     * @param sidescanLine  The sidescan line
     * @param image         The full image for sidescan line as painted in the viewer.
     * @return
     */
    public static int convertSidescanLinePointXToImagePointX(int sidescanLineX, ISidescanLine sidescanLine,
                                                             BufferedImage image) {
        int sidescanLineXSize = sidescanLine.getXSize();
        if (!sidescanLine.isImageWithSlantCorrection()) {
            return (int) (sidescanLineX / (sidescanLineXSize / (float) image.getWidth()));
        } else {
            int imgWidth = image.getWidth();
            int sspoints = sidescanLine.getXSize();
            double ximg = sidescanLineX * imgWidth / sspoints;
            double hInImg = sidescanLine.getState().getAltitude() * (imgWidth / (sidescanLine.getRange() * 2));
            double dInImg = imgWidth / 2 - ximg;
            double d = imgWidth / 2 - Math.signum(dInImg) * Math.sqrt(dInImg * dInImg - hInImg * hInImg);
            return (int) d;
        }
    }

    /**
     * Method to convert from mouse click x point in the image to sidescan x point.
     *
     * @param imageMouseX          The image x index from image
     * @param sidescanLine         The sidescan line
     * @param slantRangeCorrection To overwrite what is on sidescanLine
     * @param image                The full image for sidescan line as painted in the viewer.
     * @return
     */
    public static LocationType convertImagePointXToLocation(int imageMouseX, ISidescanLine sidescanLine,
                                                            boolean slantRangeCorrection, BufferedImage image) {
        return convertImagePointXToSidescanPoint(imageMouseX, sidescanLine, slantRangeCorrection, image).location;
    }


    /**
     * Method to convert from mouse click x point in the image to sidescan x point.
     *
     * @param imageMouseX  The image x index from image
     * @param sidescanLine The sidescan line
     * @param image        The full image for sidescan line as painted in the viewe.
     * @return
     */
    public static SidescanPoint convertImagePointXToSidescanPoint(int imageMouseX, ISidescanLine sidescanLine,
                                                                  boolean slantRangeCorrection, BufferedImage image) {
        return sidescanLine.calcPointFromIndex(convertImagePointXToSidescanLinePointX(imageMouseX, sidescanLine, image), 0,
                slantRangeCorrection);
    }



    /**
     * Method to convert from mouse click x point in the image to sidescan x point.
     *
     * @param imageMouseX  The image x index from image
     * @param sidescanLine The sidescan line
     * @param image        The full image for sidescan line as painted in the viewer.
     * @return
     */
    public static int convertImagePointXToSidescanLinePointX(int imageMouseX, ISidescanLine sidescanLine,
                                                             BufferedImage image) {
        int sidescanLineXSize = sidescanLine.getXSize();
        if (!sidescanLine.isImageWithSlantCorrection()) {
            return (int) (imageMouseX * (sidescanLineXSize / (float) image.getWidth()));
        } else {
            int imgWidth = image.getWidth();
            int sspoints = sidescanLine.getXSize();
            double hInImg = sidescanLine.getState().getAltitude() * (imgWidth / (sidescanLine.getRange() * 2));
            double d1 = Math.signum(imageMouseX - imgWidth / 2)
                    * Math.sqrt(Math.pow(imageMouseX - imgWidth / 2, 2) + hInImg * hInImg);
            double x1 = d1 + imgWidth / 2;
            double valCalcSSpx = x1 * sspoints / imgWidth;
            return (int) valCalcSSpx;
        }
    }

    /**
     * Calculates the horizontal distance from two x indexes ({@link SidescanLine#getData()}) of two
     * {@link SidescanLine}s.
     */
    public static double calcHorizontalDistanceFrom2XIndexesOf2SidescanLines(int xIndexLine1, ISidescanLine line1,
                                                                             int xIndexLine2, ISidescanLine line2) {
        return calcDistanceFrom2XIndexesOf2SidescanLines(xIndexLine1, line1, xIndexLine2, line2, true);
    }

    /**
     * Calculates the horizontal or slant distance from two x indexes ({@link
     * SidescanLine#getData()}) of two {@link SidescanLine}s.
     */
    private static double calcDistanceFrom2XIndexesOf2SidescanLines(int xIndexLine1, ISidescanLine line1,
                                                                    int xIndexLine2, ISidescanLine line2, boolean slantCorrected) {
        SidescanPoint pt2 = line2.calcPointFromIndex(xIndexLine2, 0, slantCorrected);
        SidescanPoint pt1 = line1.calcPointFromIndex(xIndexLine1, 0, slantCorrected);
        return pt1.location.getHorizontalDistanceInMeters(pt2.location);
    }

    /**
     * Calculates the slant distance (2D) from two x indexes ({@link SidescanLine#getData()}) of two
     * {@link SidescanLine}s.
     */
    public static double calcSlantDistanceFrom2XIndexesOf2SidescanLines(int xIndexLine1, ISidescanLine line1,
                                                                        int xIndexLine2, ISidescanLine line2) {
        return calcDistanceFrom2XIndexesOf2SidescanLines(xIndexLine1, line1, xIndexLine2, line2, false);
    }

    /**
     * Calculates the height of an object by the two indexes of the shadow.
     */
    public static double calcHeightFrom2XIndexesOfSidescanLine(int xIndex1, int xIndex2, ISidescanLine line) {
        double p1 = line.getDistanceFromIndex(xIndex1, false);
        double p2 = line.getDistanceFromIndex(xIndex2, false);

        // Shadow length
        double l = Math.abs(p2 - p1);
        // Altitude
        double a = line.getState().getAltitude();
        // Distance of shadow
        double r = Math.abs(Math.max(p1, p2));
        // Height
        return l * a / r;
    }

    //    /**
    //  * Converts a SonarData {@link IMCMessage} into a {@link SidescanLine} and applies the {@link SidescanParameters}.
    //  * Extracted from LSTS Neptus.
    //  * 
    //  * @param sonarData
    //  * @param pose
    //  * @param sidescanParams
    //  * @return
    //  */
    // public static SidescanLine getSidescanLine(SonarData sonarData, SystemPositionAndAttitude pose,
    //         SidescanParameters sidescanParams) {
    //     SidescanLine line = getSidescanLine((SonarData) sonarData, pose);
        
    //     if (sidescanParams != null) {
    //         double[] sData = line.getData();
    //         sData = applyNormalizationAndTVG(sData, sidescanParams);
    //         for (int i = 0; i < sData.length; i++) {
    //             line.getData()[i] = sData[i];
    //         }
    //     }

    //     return line;
    // }

    // /**
    //  * Extracted from LSTS Neptus.
    //  * @param sonarData
    //  * @param pose
    //  * @return
    //  */
    // public static SidescanLine getSidescanLine(SonarData sonarData, SystemPositionAndAttitude pose) {
    //     if (sonarData.getType() != SonarData.TYPE.SIDESCAN) {
    //         return null;
    //     }
 
    //     int range = sonarData.getMaxRange();
    //     byte[] data = sonarData.getData();
    //     double scaleFactor = sonarData.getScaleFactor();
    //     short bitsPerPoint = sonarData.getBitsPerPoint();
    //     double[] sData = getData(data, scaleFactor, bitsPerPoint);
        
    //     long timeMillis = sonarData.getTimestampMillis();
    //     long freq = sonarData.getFrequency();
    //     SidescanLine line = new SidescanLine(timeMillis, range, pose, freq, sData);
    //     return line;
    // }

    // /**
    //  * Takes the data byte array transforms it to a double array applying the scale factor. 
    //  * Extracted from LSTS Neptus.
    //  * @param data
    //  * @param scaleFactor
    //  * @param bitsPerPoint
    //  * @return
    //  */
    // private static double[] getData(byte[] data, double scaleFactor, short bitsPerPoint) {
    //     long[] longData = transformData(data, bitsPerPoint);
    //     if (longData == null)
    //         return null;
        
    //     double[] fData = new double[longData.length];
        
    //     // Lets apply scaling
    //     for (int i = 0; i < fData.length; i++) {
    //         if (fData[i] > 0) {
    //              fData[i] = longData[i] * scaleFactor;
    //         }
    //         else {
    //             // To account for 64bit unsigned long
    //             UnsignedLong ul = UnsignedLong.valueOf(Long.toUnsignedString(longData[i]));
    //             fData[i] = ul.doubleValue() * scaleFactor;
    //         }
    //     }
        
    //     return fData;
    // }

    /**
     * Transform a byte array into long (little-endian) according with bitsPerPoint.
     * Extracted from LSTS Neptus.
     * @param data
     * @param bitsPerPoint
     * @return
     */
    private static long[] transformData(byte[] data, short bitsPerPoint) {
        if (bitsPerPoint % 8 != 0 || bitsPerPoint > 64 || bitsPerPoint < 8)
            return null;
        
        int bytesPerPoint = bitsPerPoint < 8 ? 1 : (bitsPerPoint / 8);
        long[] fData = new long[data.length / bytesPerPoint];
        
        int k = 0;
        for (int i = 0; i < data.length; /* i incremented inside the 2nd loop */) {
            long val = 0;
            for (int j = 0; j < bytesPerPoint; j++) {
                int v = data[i] & 0xFF;
                v = (v << 8 * j);
                val |= v;
                i++; // progressing index of data
            }
            fData[k++] = val;
        }
        return fData;
    }

    /**
     * Extracted from LSTS Neptus.
     * @param data*
     * @param sidescanParams
     * @return
     */
    public static double[] applyNormalizationAndTVG(double[] data, SidescanParameters sidescanParams) {
        // If raw normalization is enabled, just normalize to 0-1 range
        if (sidescanParams.isRawNormalization()) {
            return applyRawNormalization(data);
        }
        
        int middle = data.length / 2;
        double[] outData = new double[data.length]; 

        double avgSboard = 0;
        double avgPboard = 0;
        for (int c = 0; c < data.length; c++) {
            double r = data[c];
            if (c < middle)
                avgPboard += r;
            else
                avgSboard += r;                        
        }
        
        avgPboard /= (double) middle * sidescanParams.getNormalization();
        avgSboard /= (double) middle * sidescanParams.getNormalization();

        
        // applying slide window
        double minVal = 0.0;
        double maxVal = 1.0;

        for (int c = 0; c < data.length; c++) {
            double r;
            double avg;
            if (c < middle) {
                r =  c / (double) middle;
                avg = avgPboard;
            }
            else {
                r =  1 - (c - middle) / (double) middle;
                avg = avgSboard;
            }
            double gain = Math.abs(30.0 * Math.log(r));
            double pb = data[c] * Math.pow(10, gain / sidescanParams.getTvgGain());
            double v = pb / avg;

            if ((minVal > 0 || maxVal < 1) && !Double.isNaN(v) && Double.isFinite(v)) {
                v = (v - minVal) / (maxVal - minVal);
            }
            outData[c] = v;
        }
        
        return outData;
    }

    /**
     * Adaptive histogram equalization for sidescan normalization.
     * This method provides better contrast by equalizing the histogram of intensities.
     * 
     * @param data The input sidescan data array
     * @param clipLimit Contrast clipping limit (0.0 to 1.0, typically 0.01-0.1)
     * @return Normalized data array with values between 0 and 1
     */
    public static double[] applyHistogramEqualization(double[] data, double clipLimit) {
        if (data == null || data.length == 0) {
            return new double[0];
        }
        
        int numBins = 256;
        double[] result = new double[data.length];
        
        // Find min/max for initial normalization
        double minVal = Double.MAX_VALUE;
        double maxVal = Double.MIN_VALUE;
        for (double value : data) {
            if (Double.isFinite(value)) {
                if (value < minVal) minVal = value;
                if (value > maxVal) maxVal = value;
            }
        }
        
        if (maxVal == minVal) {
            for (int i = 0; i < data.length; i++) {
                result[i] = 0.5;
            }
            return result;
        }
        
        // Create histogram
        int[] histogram = new int[numBins];
        int validCount = 0;
        for (double value : data) {
            if (Double.isFinite(value)) {
                int bin = (int) ((value - minVal) / (maxVal - minVal) * (numBins - 1));
                bin = Math.max(0, Math.min(numBins - 1, bin));
                histogram[bin]++;
                validCount++;
            }
        }
        
        // Apply contrast limiting (CLAHE) - simplified version
        if (clipLimit > 0 && validCount > 0) {
            int clipValue = Math.max(1, (int) (clipLimit * validCount / numBins));
            for (int i = 0; i < numBins; i++) {
                if (histogram[i] > clipValue) {
                    histogram[i] = clipValue;
                }
            }
        }
        
        // Create cumulative distribution function
        int[] cdf = new int[numBins];
        cdf[0] = histogram[0];
        for (int i = 1; i < numBins; i++) {
            cdf[i] = cdf[i - 1] + histogram[i];
        }
        
        // Find first non-zero cdf value
        int cdfMin = cdf[0];
        for (int i = 0; i < numBins && cdfMin == 0; i++) {
            cdfMin = cdf[i];
        }
        int cdfMax = cdf[numBins - 1];
        
        if (cdfMax == cdfMin) {
            for (int i = 0; i < data.length; i++) {
                result[i] = 0.5;
            }
            return result;
        }
        
        // Apply histogram equalization
        double cdfRange = cdfMax - cdfMin;
        for (int i = 0; i < data.length; i++) {
            if (Double.isFinite(data[i])) {
                int bin = (int) ((data[i] - minVal) / (maxVal - minVal) * (numBins - 1));
                bin = Math.max(0, Math.min(numBins - 1, bin));
                result[i] = (cdf[bin] - cdfMin) / cdfRange;
            } else {
                result[i] = 0.0;
            }
        }
        
        return result;
    }
    
    /**
     * Local (adaptive) histogram equalization for sidescan data.
     * Divides the data into overlapping regions and applies histogram equalization separately.
     * This prevents global equalization from over-brightening far-range data.
     * 
     * OPTIMIZED: Uses tile-based processing with bilinear interpolation instead of per-pixel regions.
     * This is ~100-200x faster than the naive per-pixel approach.
     * 
     * @param data The input sidescan data array
     * @param clipLimit Contrast clipping limit (0.0 to 1.0)
     * @param regionSize Size of each tile for local processing (typically 100-500)
     * @return Normalized data array with values between 0 and 1
     */
    public static double[] applyLocalHistogramEqualization(double[] data, double clipLimit, int regionSize) {
        if (data == null || data.length == 0) {
            return new double[0];
        }
        
        if (regionSize < 50) regionSize = 200;
        if (regionSize > data.length / 2) regionSize = data.length / 4;
        
        // Calculate tile centers with step size (reduced overlap for performance)
        int stepSize = regionSize / 2; // 50% overlap
        int numTiles = (int) Math.ceil((double) data.length / stepSize);
        
        // Pre-compute normalized tiles and their positions
        double[][] tileMaps = new double[numTiles][];
        int[] tilePositions = new int[numTiles];
        
        for (int t = 0; t < numTiles; t++) {
            int centerPos = t * stepSize;
            tilePositions[t] = centerPos;
            
            int startIdx = Math.max(0, centerPos - regionSize / 2);
            int endIdx = Math.min(data.length, centerPos + regionSize / 2);
            
            // Extract and normalize tile
            double[] tileData = new double[endIdx - startIdx];
            System.arraycopy(data, startIdx, tileData, 0, tileData.length);
            tileMaps[t] = applyHistogramEqualization(tileData, clipLimit);
        }
        
        // Interpolate between tiles for smooth transitions
        double[] result = new double[data.length];
        
        for (int i = 0; i < data.length; i++) {
            // Find surrounding tiles
            int leftTile = -1, rightTile = -1;
            
            for (int t = 0; t < numTiles; t++) {
                if (tilePositions[t] <= i) leftTile = t;
                if (tilePositions[t] >= i && rightTile == -1) {
                    rightTile = t;
                    break;
                }
            }
            
            // Handle edge cases
            if (leftTile == -1) leftTile = 0;
            if (rightTile == -1) rightTile = numTiles - 1;
            
            if (leftTile == rightTile) {
                // Inside a tile, just use it directly
                int tileCenter = tilePositions[leftTile];
                int tileStart = Math.max(0, tileCenter - regionSize / 2);
                int idxInTile = i - tileStart;
                result[i] = tileMaps[leftTile][idxInTile];
            } else {
                // Between tiles, interpolate
                int leftCenter = tilePositions[leftTile];
                int rightCenter = tilePositions[rightTile];
                double weight = (double)(i - leftCenter) / (rightCenter - leftCenter);
                
                // Get values from both tiles
                int leftTileStart = Math.max(0, leftCenter - regionSize / 2);
                int rightTileStart = Math.max(0, rightCenter - regionSize / 2);
                
                int leftIdx = i - leftTileStart;
                int rightIdx = i - rightTileStart;
                
                leftIdx = Math.max(0, Math.min(tileMaps[leftTile].length - 1, leftIdx));
                rightIdx = Math.max(0, Math.min(tileMaps[rightTile].length - 1, rightIdx));
                
                double leftVal = tileMaps[leftTile][leftIdx];
                double rightVal = tileMaps[rightTile][rightIdx];
                
                result[i] = leftVal * (1 - weight) + rightVal * weight;
            }
        }
        
        return result;
    }

    /**
     * Apply raw normalization to data - simply normalize between 0 and 1 based on min/max values.
     * No TVG, no histogram equalization, no other processing.
     * 
     * @param data The input data array
     * @return Normalized data array with values between 0 and 1
     */
    public static double[] applyRawNormalization(double[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        // Find min and max values
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        
        for (double value : data) {
            if (value < min) min = value;
            if (value > max) max = value;
        }

        // Avoid division by zero
        double range = max - min;
        if (range == 0) {
            double[] normalized = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                normalized[i] = 0.5; // All values the same, set to middle
            }
            return normalized;
        }

        // Normalize to 0-1 range
        double[] normalized = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            normalized[i] = (data[i] - min) / range;
        }

        return normalized;
    }
}
