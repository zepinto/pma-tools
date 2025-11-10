//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import com.fasterxml.jackson.annotation.*;

@lombok.Data
public class Pose {
    @lombok.Getter(onMethod_ = {@JsonProperty("altitude")})
    @lombok.Setter(onMethod_ = {@JsonProperty("altitude")})
    private Double altitude;
    @lombok.Getter(onMethod_ = {@JsonProperty("depth")})
    @lombok.Setter(onMethod_ = {@JsonProperty("depth")})
    private Double depth;
    @lombok.Getter(onMethod_ = {@JsonProperty("hacc")})
    @lombok.Setter(onMethod_ = {@JsonProperty("hacc")})
    private Double hacc;
    @lombok.Getter(onMethod_ = {@JsonProperty("height")})
    @lombok.Setter(onMethod_ = {@JsonProperty("height")})
    private Double height;
    @lombok.Getter(onMethod_ = {@JsonProperty("latitude")})
    @lombok.Setter(onMethod_ = {@JsonProperty("latitude")})
    private double latitude;
    @lombok.Getter(onMethod_ = {@JsonProperty("longitude")})
    @lombok.Setter(onMethod_ = {@JsonProperty("longitude")})
    private double longitude;
    @lombok.Getter(onMethod_ = {@JsonProperty("p")})
    @lombok.Setter(onMethod_ = {@JsonProperty("p")})
    private Double p;
    @lombok.Getter(onMethod_ = {@JsonProperty("phi")})
    @lombok.Setter(onMethod_ = {@JsonProperty("phi")})
    private Double phi;
    @lombok.Getter(onMethod_ = {@JsonProperty("psi")})
    @lombok.Setter(onMethod_ = {@JsonProperty("psi")})
    private Double psi;
    @lombok.Getter(onMethod_ = {@JsonProperty("q")})
    @lombok.Setter(onMethod_ = {@JsonProperty("q")})
    private Double q;
    @lombok.Getter(onMethod_ = {@JsonProperty("r")})
    @lombok.Setter(onMethod_ = {@JsonProperty("r")})
    private Double r;
    @lombok.Getter(onMethod_ = {@JsonProperty("theta")})
    @lombok.Setter(onMethod_ = {@JsonProperty("theta")})
    private Double theta;
    @lombok.Getter(onMethod_ = {@JsonProperty("u")})
    @lombok.Setter(onMethod_ = {@JsonProperty("u")})
    private Double u;
    @lombok.Getter(onMethod_ = {@JsonProperty("v")})
    @lombok.Setter(onMethod_ = {@JsonProperty("v")})
    private Double v;
    @lombok.Getter(onMethod_ = {@JsonProperty("w")})
    @lombok.Setter(onMethod_ = {@JsonProperty("w")})
    private Double w;
}
