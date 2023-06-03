package au.net.causal.hymie.spring;

import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
                classes = SpringExerciser.TestConfiguration.class,
                properties = {"server.port=8080"}
)
@AutoConfigureMockMvc
class SpringExerciser
{
    @LocalServerPort
    private int serverPort;

    @Test
    void test()
    {
        HttpClient httpClient = HttpClient.create()
                                          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);

        WebClient client = WebClient.builder()
                                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                                    .build();

        var r = client.get().uri(URI.create("http://localhost:" + serverPort + "/hello"));

        String response =
                r.retrieve()
                 .bodyToMono(String.class)
                 .block();

        assertThat(response).isEqualTo("Good morning, what will be for eating?");
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
        @GetMapping("/hello")
        public String hello()
        {
            return "Good morning, what will be for eating?";
        }
    }
}
