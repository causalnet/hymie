package au.net.causal.hymie;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Comparator;

public class HymieApp
{
    public static void main(String... args)
    throws IOException, AttachNotSupportedException, URISyntaxException, AgentLoadException, AgentInitializationException
    {
        //First arg is PID of VM to attach to, or else a list will be produced
        //Second arg will be agent args

        if (args.length == 0)
            listVms();
        else
        {
            long pid = Long.parseLong(args[0]);
            attachVm(pid, args.length > 1 ? args[1] : null);
        }
    }

    private static void listVms()
    {
        long currentPid = ProcessHandle.current().pid();

        System.out.println("Available VMs:");

        //Interface for using the attach API
        VirtualMachine.list().stream().sorted(Comparator.comparing(vm -> parseLongOrZero(vm.id()))).forEach( vm ->
        {
            if (vm.id().equals(String.valueOf(currentPid)))
                System.out.print("*");

            String displayName = vm.displayName();
            if (displayName == null || displayName.isEmpty())
            {
                try
                {
                    long pid = Long.parseLong(vm.id());
                    displayName = ProcessHandle.of(pid).map(processHandle -> processHandle.info().command().orElse("")).orElse("");
                }
                catch (NumberFormatException e)
                {
                    displayName = "";
                }
            }
            System.out.println(vm.id() + ": " + displayName);
        });
    }

    private static long parseLongOrZero(String s)
    {
        try
        {
            return Long.parseLong(s);
        }
        catch (NumberFormatException e)
        {
            return 0L;
        }
    }

    private static void attachVm(long pid, String agentArgs)
    throws IOException, AttachNotSupportedException, URISyntaxException, AgentLoadException, AgentInitializationException
    {
        VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));

        //Find our own JAR file
        URL aResource = HymieApp.class.getResource(HymieApp.class.getSimpleName() + ".class");
        if (aResource == null)
            throw new IOException("Could not find our own resource, cannot attach.");
        URLConnection con = aResource.openConnection();
        if (!(con instanceof JarURLConnection))
            throw new IOException("Not running agent from a JAR file, cannot attach.");

        //Load into the target process
        URL agentJar = ((JarURLConnection)con).getJarFileURL();
        Path agentJarFile = Path.of(agentJar.toURI()).toAbsolutePath();
        System.out.println("Loading agent JAR " + agentJarFile + " into process " + vm.id());

        vm.loadAgent(agentJarFile.toString(), agentArgs);

        System.out.println("Agent attached.");
    }
}
