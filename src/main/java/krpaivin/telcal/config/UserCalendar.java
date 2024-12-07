package krpaivin.telcal.config;

import java.util.HashMap;
import java.util.Map;

public class UserCalendar {
    private final Map<Integer, CalendarData> objects = new HashMap<>();

    public Map<Integer, CalendarData> getObjects() {
        return objects;
    }
    
}
