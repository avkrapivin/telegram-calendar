package krpaivin.telcal;

import java.util.logging.Logger;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import krpaivin.telcal.telegram.TelegramCalendar;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    public static void main(String[] args) {
        try {
            // Create TelegramBotsApi with new session
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Registration new bot
            botsApi.registerBot(new TelegramCalendar());
            logger.info("Bot is successfully running.");
        } catch (TelegramApiException e) {
            logger.severe("Failed to start bot: " + e.getMessage());
        }
    }
}