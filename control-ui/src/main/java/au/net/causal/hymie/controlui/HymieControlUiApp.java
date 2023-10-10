package au.net.causal.hymie.controlui;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class HymieControlUiApp
{
    private static final Logger log = LoggerFactory.getLogger(HymieControlUiApp.class);

    protected final SystemTray tray;
    private final List<Entry> processItems = new ArrayList<>(); //Only access via lock
    private final MenuItem refreshMenuItem;

    private Path hymieAgentJarFile;

    //TODO include/exclude patterns on command line

    public static void main(String... args)
    {
        //SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing;

        new HymieControlUiApp();
    }

    public HymieControlUiApp()
    {
        tray = SystemTray.get();
        tray.setImage(Objects.requireNonNull(HymieControlUiApp.class.getResource("/galah.png")));
        tray.setTooltip("Hymie");

        //If this is a Swing menu, configure Swing's look and feel to look native
        //Otherwise you get the default metal which is yuck
        if (SystemTray.SWING_UI != null)
        {
            try
            {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e)
            {
                log.error("Error setting system look and feel for Swing, menu might look ugly!", e);
            }
        }

        //Set up menus
        refreshMenuItem = new MenuItem("Refresh", e -> refreshProcesses());
        tray.getMenu().add(refreshMenuItem);
        tray.getMenu().add(new Separator());
        tray.getMenu().add(new MenuItem("Exit", e -> exitApp()));

        //Initial refresh
        updateProcesses(List.of());
    }

    private void refreshProcesses()
    {
        List<VirtualMachineDescriptor> vmList = VirtualMachine.list();
        List<Process> processList = new ArrayList<>(vmList.size());

        String ourProcessId = String.valueOf(ProcessHandle.current().pid());

        for (VirtualMachineDescriptor vmd : vmList)
        {
            //Do not put the current process into the list
            if (!ourProcessId.equals(vmd.id()))
            {
                Properties systemProperties;

                try
                {
                    VirtualMachine vm = VirtualMachine.attach(vmd);
                    try
                    {
                        systemProperties = vm.getSystemProperties();
                    }
                    finally
                    {
                        vm.detach();
                    }
                }
                catch (AttachNotSupportedException | IOException e)
                {
                    log.warn("Failed to attach to process " + vmd.displayName() + ": " + e, e);
                    systemProperties = new Properties();
                }

                processList.add(new Process(vmd, systemProperties));
            }
        }

        updateProcesses(processList);
    }

    private synchronized void updateProcesses(List<Process> processes)
    {
        //Remove all existing processes
        processItems.forEach(item -> tray.getMenu().remove(item));
        processItems.clear();

        //Create new items for processes
        int index = 0;
        if (processes.isEmpty())
        {
            MenuItem noProcessesItem = new MenuItem("(no processes, refresh to detect)");
            noProcessesItem.setEnabled(false);
            //noProcessesItem.setTooltip("Last updated: " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).format(ZonedDateTime.now()));
            processItems.add(noProcessesItem);
            tray.getMenu().add(noProcessesItem, index++);
        }
        else
        {
            List<Process> sortedProcesses = processes.stream().sorted(Comparator.comparing(Process::getNumericId).thenComparing(Process::getId)).toList();
            for (Process process : sortedProcesses)
            {
                MenuItem curItem = createProcessMenuItem(process);
                curItem.setCallback(e -> attachProcess(process, curItem));
                processItems.add(curItem);
                tray.getMenu().add(curItem, index++);
            }
        }

        //Inconsistent separator behaviour - on some platforms if separator is first item it is autoremoved so
        //if we explicitly add/remove every refresh we get consistent behaviour across platforms
        Separator sep = new Separator();
        processItems.add(sep);
        tray.getMenu().add(sep, index++);

        //Update refresh time
        refreshMenuItem.setTooltip("Last updated: " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).format(ZonedDateTime.now()));
    }

    private MenuItem createProcessMenuItem(Process process)
    {
        //Derive the name
        String name = process.getName();

        String sunJavaCommand = process.getSystemProperties().getProperty("sun.java.command");
        if (sunJavaCommand != null)
        {
            //Use the first token on the command line
            String firstToken = Arrays.stream(sunJavaCommand.split("\\s+", 2)).findFirst().orElse(null);
            if (firstToken != null)
                name = firstToken;
        }

        MenuItem menuItem = new MenuItem(process.getId() + " " + name);

        if (process.isHymieAttached())
            menuItem.setEnabled(false);

        return menuItem;
    }

    private void attachProcess(Process process, MenuItem processItem)
    {
        if (process.isHymieAttached())
            log.warn("Hymie already attached, not doing again.");
        else
        {
            log.info("Want to attach to: " + process.vmDescriptor);

            //Extract Hymie JAR to temporary file
            VirtualMachine vm;
            try
            {
                vm = VirtualMachine.attach(process.vmDescriptor);
            }
            catch (IOException | AttachNotSupportedException e)
            {
                log.warn("Failed to attach to process " + process.vmDescriptor + ": " + e, e);
                JOptionPane.showMessageDialog(null, "Error attaching to process: " + e, "Hymie", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try
            {
                Path agentJarFile = getHymieAgentJarFile();
                log.info("Will attach agent " + agentJarFile);
                String agentOptions = "mode=ui";
                vm.loadAgent(agentJarFile.toAbsolutePath().toString(), agentOptions);
            }
            catch (IOException | AgentLoadException | AgentInitializationException e)
            {
                log.error("Error loading agent into process: " + e, e);
                JOptionPane.showMessageDialog(null, "Error loading agent into process: " + e, "Hymie", JOptionPane.ERROR_MESSAGE);
                return;
            }

            processItem.setEnabled(false);
        }
    }

    private synchronized Path getHymieAgentJarFile()
    throws IOException
    {
        if (hymieAgentJarFile != null && Files.exists(hymieAgentJarFile))
            return hymieAgentJarFile;

        //Does not already exist, extract
        hymieAgentJarFile = extractHymieAgentJarFile();
        hymieAgentJarFile.toFile().deleteOnExit();

        return hymieAgentJarFile;
    }

    private Path extractHymieAgentJarFile()
    throws IOException
    {
        URL agentJarResource = HymieControlUiApp.class.getResource("/hymie-agent/hymie-agent.jar");
        if (agentJarResource == null)
            throw new IOException("Missing agent JAR resource /hymie-agent/hymie-agent.jar.");

        Path agentJarFile = Files.createTempFile("hymie-agent", ".jar");

        try (InputStream is = agentJarResource.openStream())
        {
            Files.copy(is, agentJarFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return agentJarFile;
    }

    private void exitApp()
    {
        System.exit(0);
    }

    private static class Process
    {
        private final VirtualMachineDescriptor vmDescriptor;
        private final Properties systemProperties;
        private final Long numericId;

        public Process(VirtualMachineDescriptor vmDescriptor, Properties systemProperties)
        {
            this.vmDescriptor = vmDescriptor;
            this.systemProperties = systemProperties;
            this.numericId = parseLongOrNull(vmDescriptor.id());
        }

        private static Long parseLongOrNull(String s)
        {
            try
            {
                return Long.parseLong(s);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        public String getId()
        {
            return vmDescriptor.id();
        }

        public Long getNumericId()
        {
            return numericId;
        }

        public String getName()
        {
            return vmDescriptor.displayName();
        }

        public Properties getSystemProperties()
        {
            return systemProperties;
        }

        public boolean isHymieAttached()
        {
            return getSystemProperties().containsKey("au.net.causal.hymie.HymieAgent.loaded");
        }
    }
}
