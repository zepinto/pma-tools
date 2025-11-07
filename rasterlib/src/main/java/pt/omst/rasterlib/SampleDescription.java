package pt.omst.rasterlib;

import com.fasterxml.jackson.annotation.*;
import java.time.OffsetDateTime;

/**
 * JSON schema for a sample, part of a raster accompanying file.
 */
@lombok.Data
public class SampleDescription {
    @lombok.Getter(onMethod_ = {@JsonProperty("index")})
    @lombok.Setter(onMethod_ = {@JsonProperty("index")})
    private Long index;
    @lombok.Getter(onMethod_ = {@JsonProperty("offset")})
    @lombok.Setter(onMethod_ = {@JsonProperty("offset")})
    private Long offset;
    @lombok.Getter(onMethod_ = {@JsonProperty("pose")})
    @lombok.Setter(onMethod_ = {@JsonProperty("pose")})
    private Pose pose;
    @lombok.Getter(onMethod_ = {@JsonProperty("timestamp")})
    @lombok.Setter(onMethod_ = {@JsonProperty("timestamp")})
    private OffsetDateTime timestamp;
}
