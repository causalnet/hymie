package au.net.causal.hymie.controlui;

import com.sun.tools.attach.VirtualMachineDescriptor;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@Command(name = "checksum", mixinStandardHelpOptions = true,
        versionProvider = HymieControlUiApp.HymieControlUiManifestVersionProvider.class)
public class HymieControlUiApp implements Callable<Void>
{
    @CommandLine.Option(names = "--include-class", description = "Pattern to filter class names of running applications.")
    private List<String> includeClassPatterns = new ArrayList<>();

    @CommandLine.Option(names = "--exclude-class", description = "Pattern to filter out class names of running applications.")
    private List<String> excludeClassPatterns = new ArrayList<>();

    public static void main(String... args)
    {
        //SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing;
        int exitCode = new CommandLine(new HymieControlUiApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Void call() throws Exception
    {
        HymieControlUi ui = new HymieControlUi();

        if (!includeClassPatterns.isEmpty() || !excludeClassPatterns.isEmpty())
            ui.setVmFilter(this::isVmIncluded);

        //Wait forever, GUI has control now
        while (true)
        {
            Thread.sleep(Duration.ofDays(1).toMillis());
        }
    }

    private boolean isVmIncluded(VirtualMachineDescriptor vm)
    {
        //If include list is specified, only allow through if there is a match
        if (!includeClassPatterns.isEmpty())
        {
            for (String includeClassPattern : includeClassPatterns)
            {
                boolean match = Pattern.compile(includeClassPattern).matcher(vm.displayName()).find();
                if (!match)
                    return false;
            }
        }

        for (String excludeClassPattern : excludeClassPatterns)
        {
            boolean match = Pattern.compile(excludeClassPattern).matcher(vm.displayName()).find();
            if (match)
                return false;
        }

        //Not filtered out if we get here
        return true;
    }

    static final class HymieControlUiManifestVersionProvider extends ManifestVersionProvider
    {
        public HymieControlUiManifestVersionProvider()
        {
            super(HymieControlUiApp.class);
        }
    }
}
