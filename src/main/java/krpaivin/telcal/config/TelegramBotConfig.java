package krpaivin.telcal.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TelegramBotConfig {

    private static Properties properties = new Properties();

    static {
        try (InputStream input = TelegramBotConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new FileNotFoundException("config.properties not found in resourses");
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private TelegramBotConfig() {
        // Private constuctor to prevent instantiation
    }

    public static String getBotToken() {
        return properties.getProperty("BOT_TOKEN");
    }

    public static String getAssemblyAI() {
        return properties.getProperty("AssemblyAI");
    }

    public static String getOpenAIKey() {
        return properties.getProperty("OpenAIKey");
    }

    // public static String getCalendarId() {
    //     return properties.getProperty("CalendarId");
    // }

    // public static String getUserOneId() {
    //     return properties.getProperty("UserOneId");
    // }

    // public static String getUserTwoId() {
    //     return properties.getProperty("UserTwoId");
    //}

    public static String getAssemblyAIURL() {
        return properties.getProperty("AssemblyAIURL");
    }

    public static String getOpenAIURL() {
        return properties.getProperty("OpenAIURL");
    }
    
}
