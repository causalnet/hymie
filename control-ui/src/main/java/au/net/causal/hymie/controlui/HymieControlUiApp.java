package au.net.causal.hymie.controlui;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "checksum", mixinStandardHelpOptions = true,
        versionProvider = HymieControlUiApp.HymieControlUiManifestVersionProvider.class)
public class HymieControlUiApp implements Callable<Void>
{
    //TODO include/exclude patterns on command line

    public static void main(String... args)
    {
        //SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing;
        int exitCode = new CommandLine(new HymieControlUiApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Void call() throws Exception
    {
        new HymieControlUi();

        //Wait forever, GUI has control now
        while (true)
        {
            Thread.sleep(Duration.ofDays(1).toMillis());
        }
    }

    static final class HymieControlUiManifestVersionProvider extends ManifestVersionProvider
    {
        public HymieControlUiManifestVersionProvider()
        {
            super(HymieControlUiApp.class);
        }
    }
}
