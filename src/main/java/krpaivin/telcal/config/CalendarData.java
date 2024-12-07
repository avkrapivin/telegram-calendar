package krpaivin.telcal.config;

import java.util.Map;

public class CalendarData {
    private final Map<String, String> attributes;

    public CalendarData(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
