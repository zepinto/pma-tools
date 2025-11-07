package pt.omst.licences;

public enum NeptusLicense {

    BASIC("Neptus"),
    S57("S57 Charts"),
    SHARKMARINE("SharkMarine Exporter"),
    CATL("CATL"),
    SIDESCANPRO("Sidescan Pro"),
    SIDESCANATR("Sidescan ATR"),
    RASTERFALL("Rasterfall Viewer"),
    EXPERIMENTAL("Experimental"),
    REMOTE_OPS("RemoteOps");
    private String name;

    NeptusLicense(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public boolean includes(NeptusLicense other) {
        return this.ordinal() > other.ordinal();
    }

    static final int activationDays = 365;    
}
