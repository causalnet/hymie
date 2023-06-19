package au.net.causal.hymie.socket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
                classes = NetworkExerciser.TestConfiguration.class,
                properties = {"server.port=8080"}
)
@AutoConfigureMockMvc
class NetworkExerciser
{
    @LocalServerPort
    private int serverPort;

    @Test
    void testText()
    throws Exception
    {
        URLConnection c = new URI("http://localhost:" + serverPort + "/hello").toURL().openConnection();

        try (InputStream is = c.getInputStream())
        {
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(response).isEqualTo("Good morning, what will be for eating?");
        }

        if (c instanceof HttpURLConnection hc)
            hc.disconnect();
    }

    @Test
    void testPost()
    throws Exception
    {
        URLConnection c = new URI("http://localhost:" + serverPort + "/uppercasehello").toURL().openConnection();
        c.setDoOutput(true);
        c.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
        c.setRequestProperty(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        try (OutputStream os = c.getOutputStream())
        {
            os.write("Good morning, what will be for eating?".getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream is = c.getInputStream())
        {
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            response = URLDecoder.decode(response, StandardCharsets.UTF_8);
            assertThat(response).isEqualTo("GOOD MORNING, WHAT WILL BE FOR EATING?");
        }

        if (c instanceof HttpURLConnection hc)
            hc.disconnect();
    }

    @SpringBootApplication
    public static class TestConfiguration
    {
        @Bean
        public MyRestController myRestController()
        {
            return new MyRestController();
        }
    }

    @RestController
    public static class MyRestController
    {
        @GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
        public String hello()
        {
            return "Good morning, what will be for eating?";
        }

        @PostMapping(value = "/uppercasehello", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
        public String uppercase(@RequestBody String input)
        {
            return input.toUpperCase(Locale.ROOT);
        }
    }
}
