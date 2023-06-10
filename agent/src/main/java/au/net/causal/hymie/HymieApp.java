package au.net.causal.hymie;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

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
        for (VirtualMachineDescriptor vm : VirtualMachine.list())
        {
            if (vm.id().equals(String.valueOf(currentPid)))
                System.out.print("*");

            System.out.println(vm.id() + ": " + vm.displayName());
        }
    }

    private static void attachVm(long pid, String agentArgs)
    {
        //TODO
    }
}
