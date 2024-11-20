package krpaivin.telcal.calendar;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.TelegramBotConfig;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class GoogleCalendarService {

        private GoogleCalendarService() {
                // Private constuctor to prevent instantiation
        }

        public static Credential getCredentials(final NetHttpTransport httpTransport) throws Exception {
                GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(Constants.JSON_FACTORY,
                                new InputStreamReader(GoogleCalendarService.class
                                                .getResourceAsStream(Constants.CREDENTIALS_FILE_PATH)));
                GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                                httpTransport, Constants.JSON_FACTORY, clientSecrets, Constants.SCOPES)
                                .setDataStoreFactory(new FileDataStoreFactory(
                                                new java.io.File(Constants.TOKENS_DIRECTORY_PATH)))
                                .setAccessType("offline")
                                .build();
                LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
                return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }

        public static void createGoogleCalendarEvent(String summary, String description,
                        LocalDateTime startDateTime, LocalDateTime endDateTime) throws Exception {

                final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                String calendarId = TelegramBotConfig.getCalendarId();

                Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY,
                                getCredentials(httpTransport))
                                .setApplicationName(Constants.APPLICATION_NAME)
                                .build();

                Event event = new Event().setSummary(summary).setDescription(description);

                com.google.api.services.calendar.model.Calendar calendar = service.calendars().get(calendarId)
                                .execute();
                String timeZone = calendar.getTimeZone();

                ZonedDateTime zonedStart = startDateTime.atZone(ZoneId.of(timeZone));
                ZonedDateTime zonedEnd = endDateTime.atZone(ZoneId.of(timeZone));

                EventDateTime start = new EventDateTime()
                                .setDateTime(new com.google.api.client.util.DateTime(zonedStart.toInstant().toString()))
                                .setTimeZone(timeZone);
                event.setStart(start);

                EventDateTime end = new EventDateTime()
                                .setDateTime(new com.google.api.client.util.DateTime(zonedEnd.toInstant().toString()))
                                .setTimeZone(timeZone);
                event.setEnd(end);

                service.events().insert(calendarId, event).execute();
        }

        public static String analyticsEventsByKeyword(LocalDateTime startDateTime, LocalDateTime endDateTime,
                        String keyword) throws Exception {

                String calendarId = TelegramBotConfig.getCalendarId();
                DateTime start = new DateTime(startDateTime.toString() + ":00Z");
                DateTime end = new DateTime(endDateTime.toString() + ":59Z");

                final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY,
                                getCredentials(httpTransport))
                                .setApplicationName(Constants.APPLICATION_NAME)
                                .build();

                // Get eventst list
                Calendar.Events.List request = service.events().list(calendarId)
                                .setTimeMin(start)
                                .setTimeMax(end)
                                .setSingleEvents(true)
                                .setOrderBy("startTime");

                // Add filtr keyword if it exist
                if (keyword != null && !keyword.trim().isEmpty()) {
                        request.setQ(keyword);
                }

                Events events = request.execute();
                List<Event> items = events.getItems();
                int eventCount = items.size();
                long totalDuration = 0;

                // Calculate the duration of all events
                for (Event event : items) {
                        DateTime startEvent = event.getStart().getDateTime();
                        DateTime endEvent = event.getEnd().getDateTime();

                        if (startEvent != null && endEvent != null) {
                                long eventDuration = (endEvent.getValue() - startEvent.getValue()) / (1000 * 60 * 60);
                                totalDuration += eventDuration;
                        }
                }

                return "Amount events: " + eventCount + ".\n" + "All time (hours): " + totalDuration;
        }

        public static String searchEventInCalendar(LocalDateTime startDateTime, LocalDateTime endDateTime,
                        String keyword, SearchType searchType) throws Exception {
                String calendarId = TelegramBotConfig.getCalendarId();
                DateTime start = new DateTime(startDateTime.toString() + ":00Z");
                DateTime end = new DateTime(endDateTime.toString() + ":59Z");
                String result = "Events found: \n";

                final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

                Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY,
                                getCredentials(httpTransport))
                                .setApplicationName(Constants.APPLICATION_NAME)
                                .build();

                // Get eventst list
                Calendar.Events.List request = service.events().list(calendarId)
                                .setTimeMin(start)
                                .setTimeMax(end)
                                .setOrderBy("startTime")
                                .setSingleEvents(true);

                // Add filtr keyword if it exist
                if (keyword != null && !keyword.trim().isEmpty()) {
                        request.setQ(keyword);
                }

                Events events = request.execute();
                List<Event> items = events.getItems();

                if (items.isEmpty()) {
                        result = "Events not found";
                } else {
                        switch (searchType) {
                                case FIRST:
                                        Event firstEvent = items.get(0);
                                        result = result + formatEvent(firstEvent);
                                        break;
                                case LAST:
                                        Event lastEvent = items.get(items.size() - 1);
                                        result = result + formatEvent(lastEvent);
                                        break;
                                case ALL:
                                        String eventsString = items.stream()
                                                        .map(event -> formatEvent(event))
                                                        .collect(Collectors.joining("\n"));
                                        result = result + eventsString;
                                        break;
                                default:
                                        result = "Invalid search type";
                        }
                }

                return result;
        }

        private static String formatEvent(Event event) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

                DateTime startEvent = event.getStart().getDateTime();
                DateTime endEvent = event.getEnd().getDateTime();
                LocalDateTime localstartEvent = OffsetDateTime.parse(startEvent.toStringRfc3339()).toLocalDateTime();

                String start = localstartEvent.format(formatter);

                long eventDuration = 0L;

                if (startEvent != null && endEvent != null) {
                        eventDuration = (endEvent.getValue() - startEvent.getValue()) / (1000 * 60 * 60);
                }

                return String.format("Date = %s, Duration = %d, Description = %s", start, eventDuration,
                                event.getSummary());
        }


}