package krpaivin.telcal.config;

import java.util.Collections;
import java.util.List;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.CalendarScopes;

public final class Constants {
    private Constants() {}

    public static final String APPLICATION_NAME = "Telegram Bot Google Calendar";
    public static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    public static final String TOKENS_DIRECTORY_PATH = "tokens";
    public static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    public static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    public static final String GPT_MODEL = "gpt-4o-mini";

    public static final String REQUEST_ANALYTICS = "analytics";
    public static final String REQUEST_SEARCH = "search";
    public static final String STATE = "_state";
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    public static final String DATE_PATTERN = "yyyy.MM.dd";
}
