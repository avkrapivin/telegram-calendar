package krpaivin.telcal.config;

public class Messages {
    private Messages() {}

    public static final String BOT_USERNAME = "AshkaYushkaCalendar_bot";
    public static final String BOT_TOKEN = "YOUR_BOT_TOKEN";
    public static final String B0T_UNDERCONSTRUCTION = "Sorry, the bot is temporarily unavailable due to technical work. Try again later";

    public static final String PATH_TG_API = "https://api.telegram.org/file/bot";

    public static final String BUTTON_CONFIRM = "Confirm";
    public static final String BUTTON_CANCEL = "Cancel";
    public static final String BUTTON_SETTINGS = "Connection settings";
    public static final String BUTTON_KEYWORDS = "Keywords";
    public static final String BUTTON_DEFAULT_KEYWORD = "Default keyword";
    public static final String BUTTON_COMP_KEYWORDS = "Compound keywords";
    public static final String BUTTON_CLEAR_KEYWORDS = "Clear all keywords";

    public static final String ANALYTICS = "/analytics";
    public static final String SEARCH = "/search";
    public static final String HELP = "/help";
    public static final String START = "/start";
    public static final String SETTING = "/setting";

    public static final String OPERATION_CANCEL = "Operation canceled";
    public static final String REQUEST = "Your request: ";
    public static final String WILL_BE_CREATED = "Will be created:\n";
    public static final String CALENDAR = "Calendar/";

    public static final String WHAT_SETTINGS = "What settings do you want to set?";
    public static final String SELECT_CALENDAR = "Select a calendar:";
    public static final String FOLLOW_LINK = "Follow the link, copy the code and send it to the bot";
    public static final String EVENT_CREATED = "Event created in Google Calendar.";

    public static final String REQUEST_ANALYTICST = "Send message with period and keyword (optional)";
    public static final String REQUEST_SEARCH = "Send message with period, keyword (optional) and type search (optional).";
    public static final String REQUEST_COMP_KEYWORDS = "Enter keywords to compound. Groups of words are separated by commas." +
                "For example: \"Partner1 Partner2, My family\" means that the words \"Partner1 Partner2\" will be counted " +
                "as one keyword and \"My family\" will be counted as one (other) keyword.";
    public static final String REQUEST_DEFAULT_KEYWORD = "Enter a default keyword that will be automatically set " +
                "in cases where the keyword is missing.";
    public static final String REQUEST_KEYWORDS = "Enter, separated by commas, keywords that will be set at the beginning " +
                "of the description of your event. For example, you have a shared calendar and you set the " +
                "keywords: \"Mike, Teresa\". Then you send a request: \"Tomorrow at 11 go to the store, Mike\". " +
                "The request will be processed and an event will be created for the corresponding date with the " +
                "description \"Mike. Go to the store\".";

    public static final String CALENDAR_SUCCESS = "Calendar access has been successfully configured.";
    public static final String COMP_KEYWORDS_SUCCESS = "Compound keywords access has been successfully configured.";
    public static final String DEFAULT_KEYWORD_SUCCESS = "Default keyword access has been successfully configured.";
    public static final String KEYWORDS_SUCCESS = "Keywords access has been successfully configured.";
    public static final String KEYWORDS_CLEANED = "All keywords were successfully cleaned.";

    public static final String ERROR = "Error";
    public static final String ERROR_CREATING_EVENT = "Error creating event in calendar.";
    public static final String ERROR_RECEIVING_AUDIO = "Error receiving audio file from telegram.";
    public static final String ERROR_PROCESSING_VOICE = "Error processing voice message.";
    public static final String ERROR_SAVING_CALENDAR = "Error saving calendar data.";
    public static final String ERROR_COMP_KEYWORDS = "Error saving compound keywords data.";
    public static final String ERROR_DEFAULT_KEYWORD = "Error saving default keyword data.";
    public static final String ERROR_KEYWORDS = "Error saving keywords data.";
    public static final String ERROR_CLEANING_KEYWORDS = "Error cleaning keywords.";
    public static final String ERROR_AUTHORIZATION = "Error retrieving authorization data.";
    public static final String ERROR_COLL_ANALYTICS = "Error collecting analytics.";
    public static final String ERROR_SEARCHING = "Error searching events.";
    public static final String ERROR_CREATING = "Error creating event.";
    public static final String ERROR_ACCESS_CREDETIALS = "Error accessing calendar credentials";
    public static final String ERROR_JSON_GPT = "Error processing JSON response from ChatGPT.";
    public static final String ERROR_RECEIVING_GPT = "Error receiving response from ChatGPT.";
    public static final String ERROR_ACCESSING_CALENDAR = "Error accessing calendar";

    public static final String INCORRECT_MESSAGE_FORMAT = "Incorrect message format.";
    public static final String FAILD_LOAD_CREDENTIALS = "Failed to load credentials from ";
    public static final String UNKNOWN_REQUEST_GPT = "Unknown request type to ChatGPT.";
    public static final String INVALID_URL = "Invalid URL format: ";

}
