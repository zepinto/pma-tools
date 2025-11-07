package pt.omst.licences;

public class LicenseException extends Exception {
    public LicenseException(String message) {
        super(message);
    }

    public static class LicenseNotFoundException extends LicenseException {
        public LicenseNotFoundException(String message) {
            super(message);
        }
    }

    public static class LicenseNotValidException extends LicenseException {
        public LicenseNotValidException(String message) {
            super(message);
        }
    }

    public static class LicenseExpired extends LicenseException {
        public LicenseExpired(String message) {
            super(message);
        }
    }

    public static class NoAvailableSeatsException extends LicenseException {
        public NoAvailableSeatsException(String message) {
            super(message);
        }
    }

    public static class LicenseServerUnreachable extends LicenseException {
        public LicenseServerUnreachable(String message) {
            super(message);
        }
    }

    public static class RunningInVMException extends LicenseException {
        public RunningInVMException(String message) {
            super(message);
        }
    }
}
