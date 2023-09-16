package au.net.causal.hymie.controlui;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;

import javax.swing.UIManager;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

public class HymieControlUiApp
{
    protected final SystemTray tray;
    private final List<MenuItem> processItems = new ArrayList<>(); //Only access via lock

    public static void main(String... args)
    throws Exception
    {
        //Just in case the menu UI uses Swing
        //TODO can we detect whether this is needed?
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        //SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing;

        new HymieControlUiApp();
    }

    public HymieControlUiApp()
    {
        tray = SystemTray.get();
        tray.setImage(HymieControlUiApp.class.getResource("/galah.png"));
        tray.setTooltip("Hymie");

        //Set up menus
        tray.getMenu().add(new Separator());
        tray.getMenu().add(new MenuItem("Refresh", e -> refreshProcesses()));
        tray.getMenu().add(new Separator());
        tray.getMenu().add(new MenuItem("Exit", e -> exitApp()));

        //Initial refresh
        updateProcesses(List.of());
    }

    private void refreshProcesses()
    {
        List<Process> vmList = VirtualMachine.list().stream().map(Process::new).toList();
        updateProcesses(vmList);
    }

    private synchronized void updateProcesses(List<Process> processes)
    {
        //Remove all existing processes
        processItems.forEach(item -> tray.getMenu().remove(item));
        processItems.clear();

        //Create new items for processes
        if (processes.isEmpty())
        {
            MenuItem noProcessesItem = new MenuItem("(no processes, refresh to detect)");
            noProcessesItem.setEnabled(false);
            noProcessesItem.setTooltip("Last updated: " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).format(ZonedDateTime.now()));
            processItems.add(noProcessesItem);
            tray.getMenu().add(noProcessesItem, 0);
        }
        else
        {
            for (Process process : processes)
            {
                MenuItem curItem = new MenuItem(process.getName(), e -> attachProcess(process));
                processItems.add(curItem);
                tray.getMenu().add(curItem, 0);
            }
        }
    }

    private void attachProcess(Process process)
    {
        System.out.println("Want to attach to: " + process.vmDescriptor);

        //TODO
    }

    private void exitApp()
    {
        System.exit(0);
    }

    private static class Process
    {
        private final VirtualMachineDescriptor vmDescriptor;

        public Process(VirtualMachineDescriptor vmDescriptor)
        {
            this.vmDescriptor = vmDescriptor;
        }

        public String getName()
        {
            return vmDescriptor.toString();
        }
    }
}
