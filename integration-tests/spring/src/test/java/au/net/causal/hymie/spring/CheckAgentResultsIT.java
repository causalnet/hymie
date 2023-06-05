package au.net.causal.hymie.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class CheckAgentResultsIT
{
    private static Path hymieLog;

    @BeforeAll
    static void setUp()
    {
        hymieLog = Path.of(System.getProperty("hymie.exerciser.log", "target/exerciser-hymie.log"));
    }

    @Test
    void test()
    {
        System.out.println("Check agent output file " + hymieLog);
        assertThat(hymieLog).exists().isNotEmptyFile();
    }
}
