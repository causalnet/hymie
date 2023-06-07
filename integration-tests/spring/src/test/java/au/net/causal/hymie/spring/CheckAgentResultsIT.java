package au.net.causal.hymie.spring;

import au.net.causal.hymie.json.Exchange;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class CheckAgentResultsIT
{
    private static Path hymieLog;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp()
    {
        hymieLog = Path.of(System.getProperty("hymie.exerciser.log", "target/exerciser-hymie.log"));
    }

    @Test
    void test()
    throws Exception
    {
        System.out.println("Check agent output file " + hymieLog);
        assertThat(hymieLog).exists().isNotEmptyFile();

        var results = objectMapper.readValue(hymieLog.toFile(), new TypeReference<List<Exchange>>(){});

        var exchangesByPath = results.stream().collect(Collectors.toMap(x -> x.getRequest().getPath(), x -> x));
        assertThat(exchangesByPath).containsOnlyKeys("/hello", "/hellojson", "/uppercase");

        Exchange hello = exchangesByPath.get("/hello");
        Exchange helloJson = exchangesByPath.get("/hellojson");
        Exchange uppercase = exchangesByPath.get("/uppercase");

        assertThat(hello.getRequest().getMethod()).isEqualTo(Method.GET.name());
        assertThat(hello.getRequest().getPath()).isEqualTo("/hello");
        assertThat(hello.getRequest().getBody()).isNullOrEmpty();
        assertThat(hello.getRequest().getHeaders()).isNotEmpty();
        assertThat(hello.getResponse().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
        assertThat(hello.getResponse().getHeaders()).containsEntry(HttpHeaders.CONTENT_TYPE, List.of("text/plain;charset=UTF-8"));
        assertThat(hello.getResponse().getBody()).asString(StandardCharsets.UTF_8).isEqualTo("Good morning, what will be for eating?");

        assertThat(helloJson.getRequest().getMethod()).isEqualTo(Method.GET.name());
        assertThat(helloJson.getRequest().getPath()).isEqualTo("/hellojson");
        assertThat(helloJson.getRequest().getBody()).isNullOrEmpty();
        assertThat(helloJson.getRequest().getHeaders()).isNotEmpty();
        assertThat(helloJson.getResponse().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
        assertThat(helloJson.getResponse().getHeaders()).containsEntry(HttpHeaders.CONTENT_TYPE, List.of(ContentType.APPLICATION_JSON.getMimeType()));
        SpringExerciser.HelloJson helloJsonResponse = objectMapper.readValue(helloJson.getResponse().getBody(), SpringExerciser.HelloJson.class);
        assertThat(helloJsonResponse.getMessage()).isEqualTo("Good morning, what will be for eating?");
        assertThat(helloJsonResponse.getAdditional()).isEqualTo("This is something more");

        assertThat(uppercase.getRequest().getMethod()).isEqualTo(Method.POST.name());
        assertThat(uppercase.getRequest().getPath()).isEqualTo("/uppercase");
        SpringExerciser.HelloJson uppercaseRequest = objectMapper.readValue(uppercase.getRequest().getBody(), SpringExerciser.HelloJson.class);
        assertThat(uppercaseRequest.getMessage()).isEqualTo("Greetings");
        assertThat(uppercaseRequest.getAdditional()).isEqualTo("More data");
        assertThat(uppercase.getRequest().getHeaders()).containsEntry(HttpHeaders.CONTENT_TYPE, List.of(ContentType.APPLICATION_JSON.getMimeType()));
        assertThat(uppercase.getResponse().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
        assertThat(uppercase.getResponse().getHeaders()).containsEntry(HttpHeaders.CONTENT_TYPE, List.of(ContentType.APPLICATION_JSON.getMimeType()));
        SpringExerciser.HelloJson uppercaseResponse = objectMapper.readValue(uppercase.getResponse().getBody(), SpringExerciser.HelloJson.class);
        assertThat(uppercaseResponse.getMessage()).isEqualTo("GREETINGS");
        assertThat(uppercaseResponse.getAdditional()).isEqualTo("MORE DATA");
    }
}
