package pt.omst.rasterlib;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON schema for an indexed raster accompanying file, which shall have a similar name, but
 * with a .json extension, to the raster file it accompanies.
 */
@lombok.Data
public class IndexedRaster {
    @lombok.Getter(onMethod_ = {@JsonProperty("filename")})
    @lombok.Setter(onMethod_ = {@JsonProperty("filename")})
    private String filename;
    @lombok.Getter(onMethod_ = {@JsonProperty("raster-type")})
    @lombok.Setter(onMethod_ = {@JsonProperty("raster-type")})
    private RasterType rasterType;
    @lombok.Getter(onMethod_ = {@JsonProperty("samples")})
    @lombok.Setter(onMethod_ = {@JsonProperty("samples")})
    private List<SampleDescription> samples;
    @lombok.Getter(onMethod_ = {@JsonProperty("sensor-info")})
    @lombok.Setter(onMethod_ = {@JsonProperty("sensor-info")})
    private SensorInfo sensorInfo;
}
