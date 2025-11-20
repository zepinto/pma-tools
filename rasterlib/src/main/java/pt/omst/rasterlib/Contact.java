//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import com.fasterxml.jackson.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * JSON schema for a georeferenced contact (sonar echo, photograph) together with
 * accompanying info.
 */
@lombok.Data
public class Contact {
    
    /**
     * Constructor that ensures a UUID is always present.
     */
    public Contact() {
        // Auto-generate UUID if not set
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        this.observations = new java.util.ArrayList<>();
    }
    
    @lombok.Getter(onMethod_ = {@JsonProperty("depth")})
    @lombok.Setter(onMethod_ = {@JsonProperty("depth")})
    private Double depth;
    @lombok.Getter(onMethod_ = {@JsonProperty("label")})
    @lombok.Setter(onMethod_ = {@JsonProperty("label")})
    private String label;
    @lombok.Getter(onMethod_ = {@JsonProperty("latitude")})
    @lombok.Setter(onMethod_ = {@JsonProperty("latitude")})
    private Double latitude;
    @lombok.Getter(onMethod_ = {@JsonProperty("longitude")})
    @lombok.Setter(onMethod_ = {@JsonProperty("longitude")})
    private Double longitude;
    @lombok.Getter(onMethod_ = {@JsonProperty("observations")})
    @lombok.Setter(onMethod_ = {@JsonProperty("observations")})
    private List<Observation> observations;
    @lombok.Getter(onMethod_ = {@JsonProperty("uuid")})
    @lombok.Setter(onMethod_ = {@JsonProperty("uuid")})
    private UUID uuid;
}
