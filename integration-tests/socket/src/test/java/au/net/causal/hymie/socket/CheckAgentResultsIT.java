package au.net.causal.hymie.socket;

import au.net.causal.hymie.json.Exchange;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertThat(exchangesByPath).containsOnlyKeys("/hello", "/uppercasehello");

        Exchange hello = exchangesByPath.get("/hello");
        Exchange helloUppercase = exchangesByPath.get("/uppercasehello");

        assertThat(hello.getRequest().getMethod()).isEqualTo(Method.GET.name());
        assertThat(hello.getRequest().getPath()).isEqualTo("/hello");
        assertThat(hello.getRequest().getBody()).isNullOrEmpty();
        assertThat(hello.getRequest().getHeaders()).isNotEmpty();
        assertThat(hello.getResponse().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
        assertThat(hello.getResponse().getHeaders()).containsEntry(HttpHeaders.CONTENT_TYPE, List.of("text/plain;charset=UTF-8"));
        assertThat(hello.getResponse().getBody()).asString(StandardCharsets.UTF_8).isEqualTo("Good morning, what will be for eating?");

        assertThat(helloUppercase.getRequest().getMethod()).isEqualTo(Method.POST.name());
        assertThat(helloUppercase.getRequest().getPath()).isEqualTo("/uppercasehello");
        assertThat(helloUppercase.getRequest().getBody()).asString(StandardCharsets.UTF_8).isEqualTo("Good morning, what will be for eating?");
        assertThat(helloUppercase.getRequest().getHeaders()).isNotEmpty();
        assertThat(helloUppercase.getResponse().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
        assertThat(helloUppercase.getResponse().getHeaders()).containsEntry(HttpHeaders.CONTENT_TYPE, List.of("text/plain;charset=UTF-8"));
        assertThat(helloUppercase.getResponse().getBody()).asString(StandardCharsets.UTF_8).isEqualTo("GOOD MORNING, WHAT WILL BE FOR EATING?");
    }
}
