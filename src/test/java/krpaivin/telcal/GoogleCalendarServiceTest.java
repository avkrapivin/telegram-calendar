package krpaivin.telcal;

import org.junit.jupiter.api.Test;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import krpaivin.telcal.calendar.GoogleCalendarService;
import krpaivin.telcal.calendar.SearchType;
import krpaivin.telcal.config.TelegramBotConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.LocalTime;

class GoogleCalendarServiceTest {

        @Test
	void contextLoads() {
	}
        
//     private static final String APPLICATION_NAME = "Telegram Bot Google Calendar";
//     private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
//     private TelegramBotConfig botConfig;

//     @Test
//     void testCreateGoogleCalendarEvent() throws Exception {
//         String description = "Test. Description event";
//         LocalDateTime startDateTime = LocalDateTime.now();
//         LocalDateTime endDateTime = startDateTime.plusHours(1);

//         // Create google calendar event
//         //String calendarId = TelegramBotConfig.getCalendarId();
//         String calendarId = botConfig.getCalendarId();
//         GoogleCalendarService.createGoogleCalendarEvent(description, description, startDateTime,
//                 endDateTime);

//         NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

//         com.google.api.services.calendar.Calendar service = new com.google.api.services.calendar.Calendar.Builder(
//                 httpTransport, JSON_FACTORY, GoogleCalendarService.getCredentials(httpTransport))
//                 .setApplicationName(APPLICATION_NAME)
//                 .build();

//         Events events = service.events().list(calendarId)
//                 .setQ(description)
//                 .setOrderBy("startTime")
//                 .setSingleEvents(true)
//                 .execute();

//         Event createdEvent = events.getItems().stream()
//                 .filter(event -> description.equals(event.getDescription()))
//                 .findFirst()
//                 .orElseThrow(() -> new AssertionError("Event not found"));

//         assertEquals(description, createdEvent.getSummary());

//     }

//     @Test
//     void testAnalyzeEventsByKeyword() throws Exception {
//         LocalDateTime currentDate = LocalDateTime.now();
//         LocalDateTime startDateTime = currentDate.minusMonths(2L).with(LocalTime.MIN);
//         LocalDateTime endDateTime = currentDate.plusDays(1L).with(LocalTime.MIN);

//         String response = GoogleCalendarService.analyticsEventsByKeyword(startDateTime, endDateTime, "Test. Description event");

//         assertEquals(response, "Amount events: 1.\n" + "All time (hours): 1");
//     }

//     @Test
//     void testSearchEventsByKeyword() throws Exception {
//         LocalDateTime currentDate = LocalDateTime.now();
//         LocalDateTime startDateTime = currentDate.minusMonths(2L).with(LocalTime.MIN);
//         LocalDateTime endDateTime = currentDate.plusDays(1L).with(LocalTime.MIN);

//         String response = GoogleCalendarService.searchEventInCalendar(startDateTime, endDateTime, "Test. Description event", SearchType.ALL);

//         assertEquals(response, "Events found: \n" + 
//             "Date = " + "2024-11-10 20:12" + //currentDate.format(DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN)) +
//             ", Duration = 1, Description = Test. Description event");
//     }
}
