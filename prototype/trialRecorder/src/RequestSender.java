import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;



public class RequestSender {
    private final HttpClient httpClient;

    public RequestSender() {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void sendRequest(File wavFile) throws IOException, InterruptedException {
        byte[] wavBytes = java.nio.file.Files.readAllBytes(wavFile.toPath());
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8000/transcribe?autolearn=true"))
                .header("Content-Type", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofByteArray(wavBytes))
                .build();
        System.out.println("Sending chunk to Python...");
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Python response: " + resp.statusCode() + " " + resp.body());
    }

}
