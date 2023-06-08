package au.net.causal.hymie.ui;

import javax.swing.table.DefaultTableCellRenderer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class InstantTableCellRenderer extends DefaultTableCellRenderer
{
    private final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)
                                                   .withZone(ZoneId.systemDefault());

    @Override
    protected void setValue(Object value)
    {
        if (value instanceof Instant)
            setText(formatter.format((Instant)value));
        else
            super.setValue(value);
    }
}
