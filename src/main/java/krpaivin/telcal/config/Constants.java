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
    public static final String CREDENTIALS_FILE_PATH = "config/credentials.json";
    public static final String GPT_MODEL = "gpt-4o";
    public static final String OAUTH_PATH_TOKEN = "https://oauth2.googleapis.com/token";

    public static final String BUTTON_CONFIRM_EVENT = "confirm_event";
    public static final String BUTTON_CANCEL_EVENT = "cancel_event";
    public static final String BUTTON_ALL_SETTINGS = "all_settings";
    public static final String BUTTON_CHANGE_CALENDAR = "change_calendar";
    public static final String BUTTON_KEYWORDS = "keywords";
    public static final String BUTTON_DEFAULT_KEYWORD = "default_keyword";
    public static final String BUTTON_COMPOUND_KEYWORDS = "compound_keywords";
    public static final String BUTTON_CLEAR_ALL_KEYWORDS = "clear_all_keywords";
    public static final String BUTTON_SUBMIT = "request_access";

    public static final String REQUEST_ANALYTICS = "analytics";
    public static final String REQUEST_SEARCH = "search";
    public static final String REQUEST_AUTHORIZATION = "Authorization";
    public static final String REQUEST_SET_CALENDAR = "setCalendar";
    public static final String REQUEST_SET_SETTING = "setting";
    public static final String REQUEST_COMPOUND_KEYWORDS = "compound_keywords";
    public static final String REQUEST_DEFAULT_KEYWORDS = "default_keyword";
    public static final String REQUEST_KEYWORDS = "keywords";
    public static final String REQUEST_SUBMIT = "submit";
    public static final String STATE = "_state";
    public static final String SUMBIT = "_submit";

    public static final String BD_FIELD_CALENDAR = "_calendar";
    public static final String BD_FIELD_ACCESS_TOKEN = "_accessToken";
    public static final String BD_FIELD_REFRESH_TOKEN = "_refreshToken";
    public static final String BD_FIELD_EXP_TIME_TOKEN = "_expirationTimeToken";

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    public static final String DATE_PATTERN = "dd.MM.yyyy";
    public static final String YEAR_PATTERN = "yyyy";
}
