package au.net.causal.hymie;

import com.sun.tools.attach.VirtualMachine;

import java.util.Comparator;

public class HymieApp
{
    public static void main(String... args)
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
    {
        //TODO
    }
}
