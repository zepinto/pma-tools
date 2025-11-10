//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import com.fasterxml.jackson.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JSON schema for a contact observation / identification.
 */
@lombok.Data
public class Observation {
    @lombok.Getter(onMethod_ = {@JsonProperty("annotations")})
    @lombok.Setter(onMethod_ = {@JsonProperty("annotations")})
    private List<Annotation> annotations;
    @lombok.Getter(onMethod_ = {@JsonProperty("depth")})
    @lombok.Setter(onMethod_ = {@JsonProperty("depth")})
    private double depth;
    @lombok.Getter(onMethod_ = {@JsonProperty("latitude")})
    @lombok.Setter(onMethod_ = {@JsonProperty("latitude")})
    private double latitude;
    @lombok.Getter(onMethod_ = {@JsonProperty("longitude")})
    @lombok.Setter(onMethod_ = {@JsonProperty("longitude")})
    private double longitude;
    @lombok.Getter(onMethod_ = {@JsonProperty("raster-filename")})
    @lombok.Setter(onMethod_ = {@JsonProperty("raster-filename")})
    private String rasterFilename;
    @lombok.Getter(onMethod_ = {@JsonProperty("system-name")})
    @lombok.Setter(onMethod_ = {@JsonProperty("system-name")})
    private String systemName;
    @lombok.Getter(onMethod_ = {@JsonProperty("timestamp")})
    @lombok.Setter(onMethod_ = {@JsonProperty("timestamp")})
    private OffsetDateTime timestamp;
    @lombok.Getter(onMethod_ = {@JsonProperty("user-name")})
    @lombok.Setter(onMethod_ = {@JsonProperty("user-name")})
    private String userName;
    @lombok.Getter(onMethod_ = {@JsonProperty("uuid")})
    @lombok.Setter(onMethod_ = {@JsonProperty("uuid")})
    private UUID uuid;
}
