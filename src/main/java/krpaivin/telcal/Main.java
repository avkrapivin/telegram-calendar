package krpaivin.telcal;

import java.util.logging.Logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import krpaivin.telcal.telegram.TelegramCalendar;

@SpringBootApplication
public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Main.class, args);
        try {
            TelegramCalendar telegramCalendar = context.getBean(TelegramCalendar.class);
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            
            botsApi.registerBot(telegramCalendar);

            logger.info("Bot is successfully running.");
        } catch (TelegramApiException e) {
            logger.severe("Failed to start bot: " + e.getMessage());
        }
    }
}