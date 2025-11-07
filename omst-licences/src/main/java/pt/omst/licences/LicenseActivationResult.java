package pt.omst.licences;


import lombok.Data;

@Data
public class LicenseActivationResult {
    private boolean success;
    private String message;
    private String license;
    private String errorCode;

    public static LicenseActivationResult success(String license) {
        LicenseActivationResult result = new LicenseActivationResult();
        result.setSuccess(true);
        result.setLicense(license);
        return result;
    }

    public static LicenseActivationResult error(String errorCode, String message) {
        LicenseActivationResult result = new LicenseActivationResult();
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        return result;
    }
}