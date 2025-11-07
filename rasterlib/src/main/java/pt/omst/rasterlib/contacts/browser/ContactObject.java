package pt.omst.rasterlib.contacts.browser;

public interface ContactObject {
    double getLatitude();
    double getLongitude();
    String getLabel();

    // Class to hold point data (coordinates + label)
    class Impl implements ContactObject {
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

