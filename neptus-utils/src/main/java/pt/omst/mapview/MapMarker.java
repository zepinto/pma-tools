//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

public interface MapMarker {
    double getLatitude();
    double getLongitude();
    String getLabel();

    // Class to hold point data (coordinates + label)
    class Impl implements MapMarker {
        double lat;
        double lon;
        String label;

        Impl(double lat, double lon, String label) {
            this.lat = lat;
            this.lon = lon;
            this.label = label;
        }

        @Override
        public double getLatitude() {
            return lat;
        }

        @Override
        public double getLongitude() {
            return lon;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }
}

