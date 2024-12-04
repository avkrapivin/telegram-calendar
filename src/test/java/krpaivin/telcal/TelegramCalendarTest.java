package krpaivin.telcal;

// import org.junit.Before;
// import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.junit.runner.RunWith;
// import org.mockito.ArgumentCaptor;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import org.mockito.Spy;
// import org.powermock.api.mockito.PowerMockito;
// import org.powermock.core.classloader.annotations.PrepareForTest;
// import org.powermock.modules.junit4.PowerMockRunner;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.telegram.telegrambots.meta.api.methods.GetFile;
// import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
// import org.telegram.telegrambots.meta.api.objects.File;
// import org.telegram.telegrambots.meta.api.objects.Message;
// import org.telegram.telegrambots.meta.api.objects.Update;
// import org.telegram.telegrambots.meta.api.objects.User;
// import org.telegram.telegrambots.meta.api.objects.Voice;

// import krpaivin.telcal.chatgpt.ChatGPTHadler;
// import krpaivin.telcal.chatgpt.TypeGPTRequest;
// import krpaivin.telcal.config.TelegramBotConfig;
// import krpaivin.telcal.telegram.TelegramCalendar;
// import krpaivin.telcal.telegram.VoiceCommandHandler;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.*;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// import org.mockito.*;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.test.context.junit.jupiter.SpringExtension;
// import org.springframework.test.web.servlet.MockMvc;
// import org.telegram.telegrambots.meta.api.objects.Message;
// import org.telegram.telegrambots.meta.api.objects.Update;
// import org.telegram.telegrambots.meta.api.objects.User;

// import java.lang.reflect.Field;
// import java.lang.reflect.Method;
// import java.time.LocalDateTime;
// import java.time.format.DateTimeFormatter;
// import java.util.HashMap;
// import java.util.Map;

// @SpringBootTest
// @ExtendWith(SpringExtension.class)
class TelegramCalendarTest {

    @Test
	void contextLoads() {
	}
    // @Autowired
    // private TelegramCalendar telegramCalendar;

    // @Mock
    // private Update update;

    // @Mock
    // private Message message;

    // @Mock
    // private User user;

    // private String chatId = "123456";
    // private String tomorrow = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // @BeforeEach
    // public void setUp() {
    //     // Настройка мока для User
    //     when(user.getUserName()).thenReturn(TelegramBotConfig.getUserOneId());

    //     when(message.getChatId()).thenReturn(Long.parseLong(chatId));
    //     when(message.getFrom()).thenReturn(user);
    // }

    // @Test
    // void testHandleTextMessage_createsEventSuccessfully() {
    //     String messageText = tomorrow + " 15:00 Meeting with the client";

    //     // Настройка мока для Update
    //     when(update.hasMessage()).thenReturn(true);
    //     when(update.getMessage()).thenReturn(message);

    //     // Настройка мока для Message
    //     when(message.hasText()).thenReturn(true);
    //     when(message.getText()).thenReturn(messageText);

    //     TelegramCalendar spyTelegramCalendar = spy(telegramCalendar);
    //     // Вызов метода на получение обновления
    //     telegramCalendar.onUpdateReceived(update);

            //даёт ошибку, но событие в календаре вроде создаётся
    //     // Проверка, что sendResponseMessage был вызван с ожидаемыми параметрами
    //     verify(spyTelegramCalendar, times(1)).sendResponseMessage(eq(chatId),
    //             contains("Event created in Google Calendar."));

    // }

    // @Test
    // void testHandleTextMessage_invalidFormat() {
    // String chatId = "123456";
    // String messageText = "Incorrect text";

    // User user = mock(User.class);
    // doReturn(TelegramBotConfig.getUserOneId()).when(user).getUserName();

    // doReturn(true).when(update).hasMessage();
    // doReturn(message).when(update).getMessage();
    // doReturn(true).when(message).hasText();
    // doReturn(messageText).when(message).getText();
    // doReturn(123456L).when(message).getChatId();
    // doReturn(user).when(message).getFrom();

    // telegramCalendarSpy.onUpdateReceived(update);

    // // Checking that the error message was sent
    // verify(telegramCalendarSpy).sendResponseMessage(chatId, "Incorrect message
    // format.");
    // }

    // @Test
    // void testHandleVoiceMessage() throws Exception {
    // User user = mock(User.class);
    // doReturn(TelegramBotConfig.getUserOneId()).when(user).getUserName();

    // doReturn(true).when(update).hasMessage();
    // doReturn(message).when(update).getMessage();
    // doReturn(false).when(message).hasText();
    // doReturn(true).when(message).hasVoice();
    // doReturn(12345L).when(message).getChatId();
    // doReturn(user).when(message).getFrom();

    // Voice voiceMock = mock(Voice.class);
    // doReturn(voiceMock).when(message).getVoice();
    // doReturn("mockFileId").when(voiceMock).getFileId();

    // // Mocking the execute method to return a mock File
    // File mockTelegramFile = mock(File.class);
    // doReturn(mockTelegramFile).when(telegramCalendarSpy).execute(any(GetFile.class));

    // String tomorrow =
    // LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    // VoiceCommandHandler voiceCommandHandlerSpy = spy(new VoiceCommandHandler());
    // ChatGPTHadler chatGPTHadlerSpy = spy(new ChatGPTHadler());
    // doReturn("mocked voice
    // text").when(voiceCommandHandlerSpy).convertVoiceToText(anyString());
    // doReturn(tomorrow + " 10:00 / Duration=1 / Event
    // description").when(chatGPTHadlerSpy)
    // .publicGetResponseFromChatGPT(anyString(), TypeGPTRequest.CREATING_EVENT);

    // telegramCalendarSpy.onUpdateReceived(update);

    // verify(telegramCalendarSpy,
    // times(1)).sendEventConfirmationMessage(eq("12345"),
    // contains(tomorrow + " 10:00 / Duration=1 / Event description"));

    // }

    // @Test
    // void testHandlehandleCallbackQuery() throws Exception {
    // User user = mock(User.class);
    // doReturn(TelegramBotConfig.getUserOneId()).when(user).getUserName();
    // CallbackQuery callbackQuery = mock(CallbackQuery.class);
    // doReturn("confirm_event").when(callbackQuery).getData();

    // doReturn(false).when(update).hasMessage();
    // doReturn(message).when(callbackQuery).getMessage();
    // doReturn(true).when(update).hasCallbackQuery();
    // doReturn(callbackQuery).when(update).getCallbackQuery();
    // doReturn(true).when(message).hasVoice();
    // doReturn(12345L).when(message).getChatId();
    // doReturn(user).when(message).getFrom();

    // String tomorrow =
    // LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // // Set mock-value for sessionData field
    // Map<String, String> sessionDataMock = new HashMap<>();
    // sessionDataMock.put("12345", tomorrow + " 10:00 / Duration=1 / Event
    // description");

    // Field sessionDataField =
    // telegramCalendarSpy.getClass().getDeclaredField("sessionData");
    // sessionDataField.setAccessible(true); // Opening access to a private field
    // sessionDataField.set(telegramCalendarSpy, sessionDataMock); // Set mock-data

    // Voice voiceMock = mock(Voice.class);
    // doReturn(voiceMock).when(message).getVoice();
    // doReturn("mockFileId").when(voiceMock).getFileId();

    // // Mocking the execute method to return a mock File
    // File mockTelegramFile = mock(File.class);
    // doReturn(mockTelegramFile).when(telegramCalendarSpy).execute(any(GetFile.class));

    // VoiceCommandHandler voiceCommandHandlerSpy = spy(new VoiceCommandHandler());
    // ChatGPTHadler chatGPTHadlerSpy = spy(new ChatGPTHadler());
    // doReturn("mocked voice
    // text").when(voiceCommandHandlerSpy).convertVoiceToText(anyString());
    // doReturn(tomorrow + " 10:00 / Duration=1 / Event
    // description").when(chatGPTHadlerSpy)
    // .publicGetResponseFromChatGPT(anyString(), TypeGPTRequest.CREATING_EVENT);

    // telegramCalendarSpy.onUpdateReceived(update);

    // // Checking that the createCalendarEvent method was called with the correct
    // // parameters
    // ArgumentCaptor<String> chatIdCaptor = ArgumentCaptor.forClass(String.class);
    // ArgumentCaptor<String> dateCaptor = ArgumentCaptor.forClass(String.class);
    // ArgumentCaptor<String> timeCaptor = ArgumentCaptor.forClass(String.class);
    // ArgumentCaptor<String> durationCaptor =
    // ArgumentCaptor.forClass(String.class);
    // ArgumentCaptor<String> descriptionCaptor =
    // ArgumentCaptor.forClass(String.class);

    // verify(telegramCalendarSpy).createCalendarEvent(dateCaptor.capture(),
    // timeCaptor.capture(),
    // durationCaptor.capture(), descriptionCaptor.capture(),
    // chatIdCaptor.capture());

    // assertEquals(tomorrow, dateCaptor.getValue());
    // assertEquals("10:00", timeCaptor.getValue());
    // assertEquals("1", durationCaptor.getValue());
    // assertEquals("Event description", descriptionCaptor.getValue());
    // assertEquals("12345", chatIdCaptor.getValue());
    // }

    // @Test
    // void testHandleVoiceMessageWithException() throws Exception {
    // User user = mock(User.class);
    // doReturn(TelegramBotConfig.getUserOneId()).when(user).getUserName();

    // String chatId = "12345";
    // String expectedMessage = "Error processing voice message.";

    // when(update.hasMessage()).thenReturn(true);
    // when(update.getMessage()).thenReturn(message);
    // when(message.hasVoice()).thenReturn(true);
    // when(message.getChatId()).thenReturn(12345L);
    // when(message.getVoice()).thenReturn(mock(Voice.class));
    // when(message.getVoice().getFileId()).thenReturn("mockFileId");
    // when(message.getFrom()).thenReturn(user);

    // // Mocking the execute method to return a mock File
    // File mockTelegramFile = mock(File.class);
    // doReturn(mockTelegramFile).when(telegramCalendarSpy).execute(any(GetFile.class));

    // telegramCalendarSpy.onUpdateReceived(update);

    // // Method sendResponseMessageMethod =
    // // telegramCalendarSpy.getClass().getDeclaredMethod("sendResponseMessage",
    // String.class, String.class);
    // // sendResponseMessageMethod.setAccessible(true);
    // // sendResponseMessageMethod.invoke(telegramCalendarSpy, chatId,
    // expectedMessage);

    // // Checking that an error message was sent
    // ArgumentCaptor<String> chatIdCaptor = ArgumentCaptor.forClass(String.class);
    // ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

    // verify(telegramCalendarSpy).sendResponseMessage(chatIdCaptor.capture(),
    // textCaptor.capture());

    // assertEquals(chatId, chatIdCaptor.getValue());
    // assertEquals(expectedMessage, textCaptor.getValue());
    // }
}
