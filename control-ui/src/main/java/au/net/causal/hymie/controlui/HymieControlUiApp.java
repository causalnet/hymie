package au.net.causal.hymie.controlui;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;

import javax.swing.UIManager;

public class HymieControlUiApp
{
    public static void main(String... args)
    throws Exception
    {
        //Just in case the menu UI uses Swing
        //TODO can we detect whether this is needed?
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        //SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing;

        SystemTray tray = SystemTray.get();
        tray.setImage(HymieControlUiApp.class.getResource("/galah.png"));
        tray.setTooltip("Hello there!");
        tray.getMenu().setCallback(ev -> System.out.println("Menu action!"));

        MenuItem menuItem = new MenuItem("Galah!", e -> System.err.println("You are full of galahs!"));
        menuItem.setTooltip("Don't press this one");
        tray.getMenu().add(menuItem);
        tray.getMenu().add(new MenuItem("Refresh", e ->
        {
            ((MenuItem)e.getSource()).setEnabled(false);
            tray.getMenu().add(new Separator(), 0);
            for (int i = 20; i > 0; i--)
            {
                tray.getMenu().add(new MenuItem("Process #" + i, ev -> System.err.println("Process event")), 0);
            }
        }));
        tray.getMenu().add(new Separator());
        tray.getMenu().add(new MenuItem("Exit", e ->
        {
            System.exit(0);
        }));
    }
}
