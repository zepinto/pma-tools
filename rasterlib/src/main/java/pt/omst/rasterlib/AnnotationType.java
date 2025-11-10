//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import java.io.IOException;
import com.fasterxml.jackson.annotation.*;

/**
 * Type of the annotation
 */
public enum AnnotationType {
    CLASSIFICATION, MEASUREMENT, TEXT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CLASSIFICATION: return "classification";
            case MEASUREMENT: return "measurement";
            case TEXT: return "text";
        }
        return null;
    }

    @JsonCreator
    public static AnnotationType forValue(String value) throws IOException {
        if (value.equals("classification")) return CLASSIFICATION;
        if (value.equals("measurement")) return MEASUREMENT;
        if (value.equals("text")) return TEXT;
        throw new IOException("Cannot deserialize AnnotationType");
    }
}
