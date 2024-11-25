package krpaivin.telcal.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import krpaivin.telcal.entity.UserData;

@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, UserData> userCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
    }

    @Bean
    public Cache<String, String> sessionDataCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(300)
                .build();
    }
}
