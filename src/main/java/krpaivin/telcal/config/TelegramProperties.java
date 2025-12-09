package krpaivin.telcal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "")
public class TelegramProperties {
    private String botToken;
    private String assemblyAI; 
    private String openAIKey;
    private String assemblyAIURL;
    private String openAIURL;
    private String maintenanceMode;
    private String userOneId;
    private String adminChatid;
}
