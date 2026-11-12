package app.renami.update;

/** What the GitHub latest-release probe found. */
public record UpdateInfo(String current, String latest, String appZipUrl, String cliZipUrl) {

    /** True when a strictly newer release with an installable macOS asset exists. */
    public boolean available() {
        return latest != null && appZipUrl != null
                && Version.parse(latest).isNewerThan(Version.parse(current));
    }

    /** True when this exact latest version was dismissed with "Not this version". */
    public boolean skippedAs(String skippedVersion) {
        return latest != null && latest.equals(skippedVersion);
    }
}
