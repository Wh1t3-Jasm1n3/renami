package app.renami.web.model;

/** A TV series as returned by a datasource. */
public record ShowInfo(int id, String name, String firstAirDate) {

    public int year() {
        if (firstAirDate != null && firstAirDate.length() >= 4) {
            try {
                return Integer.parseInt(firstAirDate.substring(0, 4));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
