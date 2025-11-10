//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import com.fasterxml.jackson.annotation.*;
import java.time.OffsetDateTime;

@lombok.Data
public class Annotation {
    @lombok.Getter(onMethod_ = {@JsonProperty("annotation-type")})
    @lombok.Setter(onMethod_ = {@JsonProperty("annotation-type")})
    private AnnotationType annotationType;
    @lombok.Getter(onMethod_ = {@JsonProperty("measurement-type")})
    @lombok.Setter(onMethod_ = {@JsonProperty("measurement-type")})
    private MeasurementType measurementType;
    @lombok.Getter(onMethod_ = {@JsonProperty("normalized-x")})
    @lombok.Setter(onMethod_ = {@JsonProperty("normalized-x")})
    private Double normalizedX;
    @lombok.Getter(onMethod_ = {@JsonProperty("normalized-x2")})
    @lombok.Setter(onMethod_ = {@JsonProperty("normalized-x2")})
    private Double normalizedX2;
    @lombok.Getter(onMethod_ = {@JsonProperty("normalized-y")})
    @lombok.Setter(onMethod_ = {@JsonProperty("normalized-y")})
    private Double normalizedY;
    @lombok.Getter(onMethod_ = {@JsonProperty("normalized-y2")})
    @lombok.Setter(onMethod_ = {@JsonProperty("normalized-y2")})
    private Double normalizedY2;
    @lombok.Getter(onMethod_ = {@JsonProperty("timestamp")})
    @lombok.Setter(onMethod_ = {@JsonProperty("timestamp")})
    private OffsetDateTime timestamp;
    @lombok.Getter(onMethod_ = {@JsonProperty("user-name")})
    @lombok.Setter(onMethod_ = {@JsonProperty("user-name")})
    private String userName;
    @lombok.Getter(onMethod_ = {@JsonProperty("value")})
    @lombok.Setter(onMethod_ = {@JsonProperty("value")})
    private Double value;
    @lombok.Getter(onMethod_ = {@JsonProperty("text")})
    @lombok.Setter(onMethod_ = {@JsonProperty("text")})
    private String text;
    @lombok.Getter(onMethod_ = {@JsonProperty("category")})
    @lombok.Setter(onMethod_ = {@JsonProperty("category")})
    private String category;
    @lombok.Getter(onMethod_ = {@JsonProperty("confidence")})
    @lombok.Setter(onMethod_ = {@JsonProperty("confidence")})
    private Double confidence;
    @lombok.Getter(onMethod_ = {@JsonProperty("sub-category")})
    @lombok.Setter(onMethod_ = {@JsonProperty("sub-category")})
    private String subCategory;
}
