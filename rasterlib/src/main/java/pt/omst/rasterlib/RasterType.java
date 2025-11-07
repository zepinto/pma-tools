package pt.omst.rasterlib;

import java.io.IOException;
import com.fasterxml.jackson.annotation.*;

public enum RasterType {
    IMAGE, SCANLINE, VIDEO;

    @JsonValue
    public String toValue() {
        switch (this) {
            case IMAGE: return "image";
            case SCANLINE: return "scanline";
            case VIDEO: return "video";
        }
        return null;
    }

    @JsonCreator
    public static RasterType forValue(String value) throws IOException {
        if (value.equals("image")) return IMAGE;
        if (value.equals("scanline")) return SCANLINE;
        if (value.equals("video")) return VIDEO;
        throw new IOException("Cannot deserialize RasterType");
    }
}
