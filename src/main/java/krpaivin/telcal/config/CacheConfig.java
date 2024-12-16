package krpaivin.telcal.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import krpaivin.telcal.entity.UserData;

/**
 * Configuration class for setting up application caches using the Caffeine library.
 * 
 * This class defines and initializes multiple caches with specific configurations such as
 * expiration time and maximum size.
 */
@Configuration
public class CacheConfig {
    /**
     * Creates a cache for storing user data.
     * The cache has the following characteristics:
     *     Entries expire 1 hour after last access.
     *     Maximum size is limited to 100 entries.
     * @return a {@link Cache} instance for managing {@code String -> UserData} mappings.
     */
    @Bean
    public Cache<String, UserData> userCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
    }

    /**
     * Creates a cache for storing session-related data.
     * The cache has the following characteristics:
     *     Entries expire 1 hour after last access.</li>
     *     Maximum size is limited to 300 entries.</li>
     * @return a {@link Cache} instance for managing {@code String -> String} mappings.
     */
    @Bean
    public Cache<String, String> sessionDataCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(300)
                .build();
    }

    /**
     * Creates a cache for storing user calendar selections.
     * The cache has the following characteristics:
     *     Entries expire 1 hour after last access.
     *     Maximum size is limited to 300 entries.
     * @return a {@link Cache} instance for managing {@code String -> UserCalendar} mappings.
     */
    @Bean
    public Cache<String, UserCalendar> calendarSelectionCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(300)
                .build();
    }
}
