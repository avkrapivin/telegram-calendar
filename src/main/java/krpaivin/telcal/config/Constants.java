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

    public static final String BUTTON_CONFIRM_EVENT = "confirm_event";
    public static final String BUTTON_CANCEL_EVENT = "cancel_event";
    public static final String BUTTON_ALL_SETTINGS = "all_settings";
    public static final String BUTTON_CHANGE_CALENDAR = "change_calendar";

    public static final String REQUEST_ANALYTICS = "analytics";
    public static final String REQUEST_SEARCH = "search";
    public static final String REQUEST_AUTHORIZATION = "Authorization";
    public static final String REQUEST_SET_CALENDAR = "setCalendar";
    public static final String REQUEST_SET_SETTING = "setting";
    public static final String STATE = "_state";

    public static final String BD_FIELD_CALENDAR = "_calendar";
    public static final String BD_FIELD_ACCESS_TOKEN = "_accessToken";
    public static final String BD_FIELD_REFRESH_TOKEN = "_refreshToken";
    public static final String BD_FIELD_EXP_TIME_TOKEN = "_expirationTimeToken";

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    public static final String DATE_PATTERN = "yyyy.MM.dd";
}
