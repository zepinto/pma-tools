package pt.omst.rasterlib;

import com.fasterxml.jackson.annotation.*;
import java.util.List;

@lombok.Data
public class SensorInfo {
    @lombok.Getter(onMethod_ = {@JsonProperty("color-mode")})
    @lombok.Setter(onMethod_ = {@JsonProperty("color-mode")})
    private String colorMode;
    @lombok.Getter(onMethod_ = {@JsonProperty("filters")})
    @lombok.Setter(onMethod_ = {@JsonProperty("filters")})
    private List<String> filters;
    @lombok.Getter(onMethod_ = {@JsonProperty("frequency")})
    @lombok.Setter(onMethod_ = {@JsonProperty("frequency")})
    private Double frequency;
    @lombok.Getter(onMethod_ = {@JsonProperty("hfov")})
    @lombok.Setter(onMethod_ = {@JsonProperty("hfov")})
    private Double hfov;
    @lombok.Getter(onMethod_ = {@JsonProperty("max-range")})
    @lombok.Setter(onMethod_ = {@JsonProperty("max-range")})
    private Double maxRange;
    @lombok.Getter(onMethod_ = {@JsonProperty("min-range")})
    @lombok.Setter(onMethod_ = {@JsonProperty("min-range")})
    private Double minRange;
    @lombok.Getter(onMethod_ = {@JsonProperty("sensor-model")})
    @lombok.Setter(onMethod_ = {@JsonProperty("sensor-model")})
    private String sensorModel;
    @lombok.Getter(onMethod_ = {@JsonProperty("system-name")})
    @lombok.Setter(onMethod_ = {@JsonProperty("system-name")})
    private String systemName;
    @lombok.Getter(onMethod_ = {@JsonProperty("vfov")})
    @lombok.Setter(onMethod_ = {@JsonProperty("vfov")})
    private Double vfov;
}
