package app.renami.web.model;

/** A movie as returned by a datasource. */
public record MovieInfo(int id, String title, String releaseDate) {

    public int year() {
        if (releaseDate != null && releaseDate.length() >= 4) {
            try {
                return Integer.parseInt(releaseDate.substring(0, 4));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
