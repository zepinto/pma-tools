package pt.omst.rasterlib;

import java.io.IOException;
import com.fasterxml.jackson.annotation.*;

public enum MeasurementType {
    BOX, HEIGHT, LENGTH, SIZE, WIDTH;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BOX: return "box";
            case HEIGHT: return "height";
            case LENGTH: return "length";
            case SIZE: return "size";
            case WIDTH: return "width";
        }
        return null;
    }

    @JsonCreator
    public static MeasurementType forValue(String value) throws IOException {
        if (value.equals("box")) return BOX;
        if (value.equals("height")) return HEIGHT;
        if (value.equals("length")) return LENGTH;
        if (value.equals("size")) return SIZE;
        if (value.equals("width")) return WIDTH;
        throw new IOException("Cannot deserialize MeasurementType");
    }
}
