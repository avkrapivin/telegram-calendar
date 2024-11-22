package krpaivin.telcal.data;

import java.nio.file.Paths;
import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.file.Files;
import org.json.JSONObject;

public class CredentialsLoader {
    private CredentialsLoader() {
    }

    public static JSONObject loadCredentials(String filePath) {
        try {
            // Get URL from classpath
            URL resource = CredentialsLoader.class.getClassLoader().getResource(filePath);
            if (resource == null) {
                throw new FileNotFoundException("Resource not found: " + filePath);
            }

            // Convert URL to path and read file
            String content = new String(Files.readAllBytes(Paths.get(resource.toURI())));
            return new JSONObject(content);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
