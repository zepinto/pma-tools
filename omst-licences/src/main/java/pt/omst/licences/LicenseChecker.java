package pt.omst.licences;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import javax0.license3j.License;
import javax0.license3j.io.IOFormat;
import javax0.license3j.io.LicenseReader;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;
import pt.omst.licences.util.ConfigFetch;

public class LicenseChecker {
    private final static Logger LOG = LoggerFactory.getLogger(LicenseChecker.class);
    private final static String licensesFolder = ConfigFetch.resolvePath("conf/licenses/");
    private final static String LICENSE_SERVER_URL = "licenses.omst.pt";

    private static final byte[] pubKey = new byte[]{
            (byte) 0x52,
            (byte) 0x53, (byte) 0x41, (byte) 0x00, (byte) 0x30, (byte) 0x81, (byte) 0x9F, (byte) 0x30, (byte) 0x0D,
            (byte) 0x06, (byte) 0x09, (byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xF7, (byte) 0x0D,
            (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81, (byte) 0x8D,
            (byte) 0x00, (byte) 0x30, (byte) 0x81, (byte) 0x89, (byte) 0x02, (byte) 0x81, (byte) 0x81, (byte) 0x00,
            (byte) 0xB4, (byte) 0x34, (byte) 0x3C, (byte) 0xE3, (byte) 0xC1, (byte) 0xC1, (byte) 0x54, (byte) 0x3C,
            (byte) 0x11, (byte) 0x7C, (byte) 0x82, (byte) 0x56, (byte) 0x07, (byte) 0x32, (byte) 0x14, (byte) 0x2A,
            (byte) 0x8E, (byte) 0xB3, (byte) 0x68, (byte) 0x12, (byte) 0x3A, (byte) 0x79, (byte) 0xED, (byte) 0x00,
            (byte) 0xE3, (byte) 0x69, (byte) 0x5E, (byte) 0xB7, (byte) 0x54, (byte) 0x98, (byte) 0xA8, (byte) 0xA1,
            (byte) 0xC4, (byte) 0x3A, (byte) 0x87, (byte) 0x51, (byte) 0x19, (byte) 0xC6, (byte) 0x31, (byte) 0x5B,
            (byte) 0xB0, (byte) 0x1A, (byte) 0x68, (byte) 0xDE, (byte) 0x3F, (byte) 0x43, (byte) 0xA4, (byte) 0x6E,
            (byte) 0xE2, (byte) 0xF3, (byte) 0xA1, (byte) 0x14, (byte) 0x27, (byte) 0xC3, (byte) 0x20, (byte) 0x09,
            (byte) 0xC6, (byte) 0xCA, (byte) 0x98, (byte) 0x38, (byte) 0x81, (byte) 0x08, (byte) 0xED, (byte) 0xB9,
            (byte) 0x7E, (byte) 0xA4, (byte) 0xD8, (byte) 0x6B, (byte) 0xF5, (byte) 0x52, (byte) 0x1B, (byte) 0xC2,
            (byte) 0x3F, (byte) 0xA9, (byte) 0x81, (byte) 0x6F, (byte) 0x62, (byte) 0x72, (byte) 0x8B, (byte) 0x3A,
            (byte) 0x9F, (byte) 0x26, (byte) 0xDD, (byte) 0x5F, (byte) 0x55, (byte) 0x84, (byte) 0x26, (byte) 0xEA,
            (byte) 0x71, (byte) 0x05, (byte) 0xE4, (byte) 0x17, (byte) 0x86, (byte) 0x09, (byte) 0x47, (byte) 0xC9,
            (byte) 0x7B, (byte) 0x08, (byte) 0x2E, (byte) 0x6A, (byte) 0xCE, (byte) 0x94, (byte) 0xAC, (byte) 0xAA,
            (byte) 0xC8, (byte) 0x7B, (byte) 0xFD, (byte) 0x89, (byte) 0x44, (byte) 0xEB, (byte) 0xE4, (byte) 0x59,
            (byte) 0x81, (byte) 0x37, (byte) 0x9E, (byte) 0x5E, (byte) 0xF7, (byte) 0xF9, (byte) 0x99, (byte) 0x6B,
            (byte) 0xE6, (byte) 0xD3, (byte) 0x14, (byte) 0x5F, (byte) 0xF6, (byte) 0x71, (byte) 0xC8, (byte) 0x1F,
            (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x01,
    };

    public static void releaseLicense() throws LicenseException {
        String licenseId = getLicenseId();
        String computerId = getComputerId();
        if (licenseId == null) {
            throw new LicenseException.LicenseNotFoundException("No license found");
        }
        if (computerId == null) {
            throw new LicenseException("No computer ID found");
        }

        try {
            String url = "https://" + LICENSE_SERVER_URL + "/deactivate?id=" + URLEncoder.encode(licenseId, StandardCharsets.UTF_8) +
                    "&computerId=" + URLEncoder.encode(computerId, StandardCharsets.UTF_8);
            //LOG.info("connecting to " + url);

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                LOG.info("License deactivated");
            } else if (responseCode == 302) {
                for (int i = 0; i < 10; i++) {
                    connection = (HttpURLConnection) new URL(connection.getHeaderField("Location")).openConnection();
                    LOG.info("Connecting to " + connection.getURL());
                    connection.setRequestMethod("GET");
                    connection.connect();
                    responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        LOG.info("License deactivated");
                        break;
                    }
                }
            } else {
                throw new Exception("Failed to deactivate license, response: " + responseCode + ": " + connection.getResponseMessage());
            }
            Files.delete(new File(licensesFolder, licenseId + ".act").toPath());
            LOG.info("removed activation file " + licenseId + ".act");
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error(e.getMessage());
            throw new LicenseException.LicenseServerUnreachable(e.getMessage());
        }
    }

    private static void activateLicense(int days) throws LicenseException {
        String licenseId = getLicenseId();
        String computerId = getComputerId();
        if (licenseId == null) {
            LOG.warn("No license found.");
            throw new LicenseException.LicenseNotFoundException("No license found");
        }
        if (computerId == null) {
            LOG.warn("Could not get get computer Id");
            throw new LicenseException("No computer ID found");
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://" + LICENSE_SERVER_URL + "/activate?id=" + licenseId +
                    "&computerId=" + computerId +
                    "&os=" + getOsName() +
                    "&username=" + getUsername() +
                    "&version=undetermined" +
                    "&days=" + days
            ).openConnection();

            //LOG.info("connecting to " + connection.getURL());

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            String response = new String(connection.getInputStream().readAllBytes());
            if (!response.startsWith("{")) {
                LOG.warn("Response not understood: " + response);
                throw new LicenseException.LicenseNotValidException("License Server Response: " + response);
            }

            Gson gson = new Gson();

            LicenseActivationResult result = gson.fromJson(response, LicenseActivationResult.class);
            if (result.isSuccess()) {
                String license = result.getLicense();
                Files.write(new File(licensesFolder, licenseId + ".act").toPath(), license.getBytes());
                LOG.info("License has been activated.");
            } else {
                switch (result.getErrorCode()) {
                    case "license_limit_reached":
                        LOG.warn("No available seats.");
                        throw new LicenseException.NoAvailableSeatsException("No available seats");
                    case "license_expired":
                        LOG.warn("License is expired.");
                        throw new LicenseException.LicenseExpired("License expired");
                    case "license_not_found":
                        LOG.warn("License not found in the server.");
                        throw new LicenseException.LicenseNotFoundException("License not found");
                    case "license_not_valid":
                        LOG.warn("License is not valid (on the server).");
                        throw new LicenseException.LicenseNotValidException("License not valid");
                    default:
                        LOG.warn("Unknown license error.");
                        throw new LicenseException.LicenseNotValidException("License Error: " + result.getErrorCode());
                }
            }
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new LicenseException.LicenseServerUnreachable("Server is unreacheable: "+e.getMessage());
        }
    }

    private static License mainLicense = null, activationLicense = null;

    public synchronized static License getMainLicense() throws LicenseException {
        if (mainLicense != null) {
            return mainLicense;
        }

        File licenseFolder = new File(licensesFolder);
        if (!licenseFolder.exists()) {
            LOG.info("No license folder found");
            return null;
        }

        File[] files = Objects.requireNonNull(licenseFolder.listFiles());
        if (files.length == 0) {
            LOG.info("No license found");
            return null;
        }

        Arrays.sort(files);

        ArrayList<LicenseException> exceptions = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().endsWith(".lic")) {
                continue;
            }
            try {
                License license = checkMainLicense(f);
                if (license != null) {
                    mainLicense = license;
                    return mainLicense;
                }
            } catch (LicenseException e) {
                exceptions.add(e);
            }
        }
        if (mainLicense != null) {
            return mainLicense;
        }
        if (exceptions.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (LicenseException e : exceptions) {
                sb.append(e.getMessage()).append("\n");
            }
            throw new LicenseException.LicenseNotValidException(sb.toString());
        }
        throw new LicenseException.LicenseNotFoundException("No valid license found");
    }

    public static String getFeatures() {
        try {
            ArrayList<String> features = new ArrayList<>();
            features.addAll(getLicenseActivation().getFeatures().keySet());
            features.remove("expiryDate");
            features.remove("license-id");
            features.remove("licenseSignature");
            features.remove("signatureDigest");
            features.remove("computer-id");
            return String.join(", ", features);
        } catch (Exception e) {

        }
        return "";
    }

    public static License getLicenseActivation() throws LicenseException {
        return getLicenseActivation(false, true);
    }

    public synchronized static License getLicenseActivation(boolean forceRead, boolean retry) throws LicenseException {
        if (activationLicense != null && !forceRead) {
            return activationLicense;
        }

        LOG.info("Reading activation license for " + getLicenseId());
        String id = getLicenseId();
        
        File f = new File(licensesFolder, id + ".act");
        if (!f.exists()) {
            LOG.info("No activation license found, activating license");
            activateLicense(NeptusLicense.activationDays);
        }

        try (LicenseReader reader = new LicenseReader(f)) {
            License l = reader.read(IOFormat.STRING);   
            if (!l.isOK(pubKey)) {
                Files.delete(f.toPath());
                LOG.info("Invalid signature for activation license, removing file " + f.getName());
                if (retry) {
                    return getLicenseActivation(forceRead, false);
                }
                else {
                    throw new LicenseException.LicenseNotValidException("Invalid license signature for file " + f.getName());
                }
            }
            String computerId = l.getFeatures().get("computer-id").getString().replaceAll("[^a-zA-Z0-9]", "");
            String otherId = getComputerId().replaceAll("[^a-zA-Z0-9]", "");
            if (!computerId.equals(otherId)) {
                Files.delete(f.toPath());
                LOG.info("Computer id does not match license activation, removing file " + f.getName());
                if (retry) {
                    return getLicenseActivation(forceRead, false);
                }
                else {
                    throw new LicenseException.LicenseNotValidException(computerId + " does not match " + otherId + " for activation license");
                }
            }
            if (l.isExpired()) {
                Files.delete(f.toPath());
                LOG.info("Activation license expired, removing file " + f.getName());
                if (retry) {
                    return getLicenseActivation(forceRead, false);
                }
                else {
                    throw new LicenseException.LicenseExpired("Activation license expired");
                }
            }
            activationLicense = l;
            return l;
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new LicenseException.LicenseNotValidException(e.getMessage());
        }
    }

    public static String getLicenseId() throws LicenseException {
        License license = getMainLicense();
        if (license == null) {
            throw new LicenseException.LicenseNotFoundException("No license found in the licenses folder.");
        }
        return license.get("id").getString();
    }

    public static String getUsername() {
        return URLEncoder.encode(System.getProperty("user.name"));
    }

    public static String getOsName() {
        return URLEncoder.encode(System.getProperty("os.name"));
    }

    public static String getComputerId() throws LicenseException {
        if (runningInVirtualMachine()) {
            throw new LicenseException.RunningInVMException("Running in a virtual machine");
        }

        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
        HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
        CentralProcessor centralProcessor = hardwareAbstractionLayer.getProcessor();
        String vendor = operatingSystem.getManufacturer();
        String processorIdentifier = hardwareAbstractionLayer.getProcessor().getProcessorIdentifier().getProcessorID();
        int processors = centralProcessor.getLogicalProcessorCount();

        String delimiter = "_";

        String id = vendor +
                delimiter +
                processorIdentifier +
                delimiter +
                processors;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(id.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new LicenseException("Failed to generate computer ID");
        }
    }

    public static boolean runningInVirtualMachine() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
        ComputerSystem computerSystem = hardwareAbstractionLayer.getComputerSystem();
        return computerSystem.getManufacturer().contains("VMware") || computerSystem.getModel().contains("Virtual");
    }

    private static License checkMainLicense(File f) throws LicenseException {
        try (LicenseReader reader = new LicenseReader(f)) {
            License license = reader.read(IOFormat.STRING);
            if (!license.isOK(pubKey)) {
                throw new LicenseException.LicenseNotValidException("Invalid license signature for file " + f.getName());
            }
            if (license.isExpired())
                throw new LicenseException.LicenseExpired("License " + f.getName() + " is expired");

            if (license.getFeatures().containsKey("id"))
                return license;
            else
                return null;
        } catch (IOException e) {
            throw new LicenseException.LicenseNotValidException(e.getMessage());
        }
    }

    public static boolean checkLicense(NeptusLicense feature) throws LicenseException {
        return checkLicense(feature.toString());
    }

    public static boolean checkLicense(String feature) throws LicenseException {
        return getLicenseActivation().getFeatures().containsKey(feature);
    }

    public static boolean checkFeature(String featureName) {
        try {
            License license = getLicenseActivation();
            if (license.isExpired())
                return false;
            return license.getFeatures().containsKey(featureName);
        } catch (Exception e) {
            LOG.error(e.getMessage());
            return false;
        }
    }

    public static int daysToExpiry() {
        try {
            License license = getLicenseActivation();
            if (license == null) {
                return -1;
            }

            Date expiryDate = license.get("expiryDate").getDate();
            Date now = new Date();
            long diff = expiryDate.getTime() - now.getTime();
            return (int) (diff / (24 * 60 * 60 * 1000));
        } catch (Exception e) {
            LOG.error(e.getMessage());
            return -1;
        }
    }

    public static void reactivateIfNeeded(int days) {
        if (daysToExpiry() < (days / 2)) {
            try {
                reactivateLicense(days);
            } catch (LicenseException e) {
                LOG.error(e.getMessage());
            }
        }
    }

    public static void reactivateLicense(int days) throws LicenseException {
        activateLicense(days);
    }

    public static ArrayList<String> getLicensedFeatures() {
        try {
            return new ArrayList<>(getLicenseActivation().getFeatures().keySet());
        } catch (Exception e) {
            LOG.error(e.getMessage());
            return new ArrayList<>();
        }
    }

    public static boolean isLicensed(NeptusLicense c) {
        try {
            return LicenseChecker.checkLicense(c);
        } catch (LicenseException e) {
            return false;
        }
    }

    public static void main(String[] args) {

        long start = System.currentTimeMillis();
        try {
            System.out.println(getLicenseActivation().getFeatures());
            long end = System.currentTimeMillis();
            System.out.println("License OK in " + (end - start) + " ms");
            System.out.println(getLicenseActivation().getFeatures());
            long end2 = System.currentTimeMillis();
            System.out.println("License OK in " + (end2 - end) + " ms");
            System.out.println(daysToExpiry());

        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }
}
