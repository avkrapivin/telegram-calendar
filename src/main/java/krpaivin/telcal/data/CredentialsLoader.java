package krpaivin.telcal.data;

import java.nio.file.Paths;
import java.util.Optional;
import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.file.Files;
import org.json.JSONObject;

/**
 * Utility class for loading credentials from a file located in the classpath.
 */
public class CredentialsLoader {
    private CredentialsLoader() {
    }

    /**
     * Loads credentials from a specified file path in the classpath and returns them as a {@link JSONObject}.
     *
     * The method attempts to locate the file using the class loader, read its contents, and parse it as a JSON object.
     * If the file cannot be found or an error occurs during reading or parsing, the method prints the stack trace
     * and returns {@code null}.
     *
     * @param filePath the path to the credentials file in the classpath.
     * @return a {@link JSONObject} representing the loaded credentials, or {@code null} if an error occurs.
     * @throws FileNotFoundException if the file specified by {@code filePath} cannot be located in the classpath.
     */
    public static JSONObject loadCredentials(String filePath) {
        try {
            // Get URL from classpath
            URL resource = Optional.ofNullable(CredentialsLoader.class.getClassLoader().getResource(filePath))
                    .orElseThrow(() -> new FileNotFoundException("Resource not found: " + filePath));

            // Convert URL to path and read file
            String content = new String(Files.readAllBytes(Paths.get(resource.toURI())));
            return new JSONObject(content);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
