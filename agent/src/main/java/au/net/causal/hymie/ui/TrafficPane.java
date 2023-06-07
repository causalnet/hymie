package au.net.causal.hymie.ui;

import au.net.causal.hymie.HttpExchangeParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class TrafficPane extends JPanel
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JTable trafficTable;
    private TrafficTableModel trafficTableModel;

    private final JLabel trafficDetailsPane;

    public TrafficPane()
    {
        super(new BorderLayout());

        trafficTable = new JTable();
        trafficTable.getSelectionModel().addListSelectionListener(ev -> trafficTableSelectionUpdated());
        trafficTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        trafficDetailsPane = new JLabel("Details go here");

        setLayout(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true,
                new JScrollPane(trafficTable), new JScrollPane(trafficDetailsPane));
        add(splitPane, BorderLayout.CENTER);
    }

    public void setTraffic(Map<Integer, ? extends HttpExchangeParser.Exchange> trafficMap)
    {
        //Update traffic table
        trafficTableModel = new TrafficTableModel(trafficMap);
        trafficTable.setModel(trafficTableModel);
    }

    private void trafficTableSelectionUpdated()
    {
        int rowIndex = trafficTable.getSelectedRow();
        if (rowIndex < 0)
            trafficTableEntrySelected(null);
        else
            trafficTableEntrySelected(trafficTableModel.getRowAt(rowIndex));
    }

    protected void trafficTableEntrySelected(TrafficTableModel.Entry entry)
    {
        if (entry == null)
            System.out.println("Selected: nothing");
        else
            System.out.println("Selected: " + entry.getId() + ": " + entry.getPath());

        trafficDetailsPane.setText("ID: " + entry.getId() + "\n" +
                entry.getHttpMethod() + " " + entry.getPath() + "\n" +
                "Request:\n" +
                entry.getRequestContent() + "\n" +
                "Response:\n" +
                entry.getResponseContent() + "\n"
        );
    }

    private class TrafficTableModel extends AbstractTableModel
    {
        //Columns: ID, timestamp, address, path, HTTP method, request byte count, response byte count
        private static final List<String> COLUMN_NAMES = List.of(
                "ID", "Timestamp", "Address", "Path", "Method", "Request Size", "Response Size"
        );

        private final List<Entry> trafficEntries;

        public TrafficTableModel(Map<Integer, ? extends HttpExchangeParser.Exchange> trafficMap)
        {
            this.trafficEntries = trafficMap.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
        }

        @Override
        public int getRowCount()
        {
           return trafficEntries.size();
        }

        @Override
        public int getColumnCount()
        {
            return COLUMN_NAMES.size();
        }

        public Entry getRowAt(int rowIndex)
        {
            return trafficEntries.get(rowIndex);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            Entry entry = getRowAt(rowIndex);
            return switch (columnIndex) {
                case 0 ->  entry.getId();
                case 1 -> entry.getTimestamp();
                case 2 -> entry.getAddress();
                case 3 -> entry.getPath();
                case 4 -> entry.getHttpMethod();
                case 5 -> entry.getRequestSize();
                case 6 -> entry.getResponseSize();
                default -> null;
            };
        }

        @Override
        public String getColumnName(int column)
        {
            return COLUMN_NAMES.get(column);
        }

        public class Entry
        {
            private final long id;
            private final HttpExchangeParser.Exchange exchange;

            public Entry(long id, HttpExchangeParser.Exchange exchange)
            {
                this.id = id;
                this.exchange = exchange;
            }

            public long getId()
            {
                return id;
            }

            public Instant getTimestamp()
            {
                //TODO
                return LocalDateTime.of(1977, 9, 1, 12, 0).toInstant(ZoneOffset.UTC);
            }

            public String getAddress()
            {
                return exchange.getAddress().toString();
            }

            public String getPath()
            {
                return exchange.getRequest().getPath();
            }

            public String getHttpMethod()
            {
                return exchange.getRequest().getMethod();
            }

            public long getRequestSize()
            {
                return entitySize(exchange.getRequest().getEntity());
            }

            public long getResponseSize()
            {
                return entitySize(exchange.getResponse().getEntity());
            }

            public String getRequestContent()
            {
                return entityContent(exchange.getRequest().getEntity());
            }

            public String getResponseContent()
            {
                return entityContent(exchange.getResponse().getEntity());
            }

            private long entitySize(HttpEntity entity)
            {
                if (entity == null)
                    return 0L;
                else if (entity.getContentLength() >= 0L)
                    return entity.getContentLength();
                else
                {
                    try
                    {
                        return entity.getContent().readAllBytes().length; //TODO inefficient
                    }
                    catch (IOException e)
                    {
                        return 0L;
                    }
                }
            }

            private String entityContent(HttpEntity entity)
            {
                if (entity == null)
                    return "";
                else
                {
                    try
                    {
                        ContentType contentType = ContentType.parseLenient(entity.getContentType());

                        try (InputStream content = entity.getContent())
                        {
                            if (ContentType.APPLICATION_JSON.isSameMimeType(contentType))
                                return objectMapper.readTree(content).toPrettyString();
                            else
                                return new String(content.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    }
                    catch (IOException e)
                    {
                        return e.toString();
                    }
                }
            }
        }
    }
}
