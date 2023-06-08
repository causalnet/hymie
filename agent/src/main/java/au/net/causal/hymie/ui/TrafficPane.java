package au.net.causal.hymie.ui;

import au.net.causal.hymie.HttpExchangeParser;
import au.net.causal.hymie.formatter.MessageFormatterRegistry;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TrafficPane extends JPanel
{
    private final MessageFormatterRegistry messageFormatterRegistry;

    private final JTable trafficTable;
    private TrafficTableModel trafficTableModel;

    private final JTextArea requestHeadersPane;
    private final JTextArea responseHeadersPane;
    private final RSyntaxTextArea requestBodyPane;
    private final RSyntaxTextArea responseBodyPane;

    public TrafficPane(MessageFormatterRegistry messageFormatterRegistry)
    {
        super(new BorderLayout());

        this.messageFormatterRegistry = messageFormatterRegistry;

        trafficTable = new JTable();
        trafficTable.getSelectionModel().addListSelectionListener(ev -> trafficTableSelectionUpdated());
        trafficTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trafficTable.setDefaultRenderer(Instant.class, new InstantTableCellRenderer());

        requestHeadersPane = new JTextArea();
        requestHeadersPane.setEditable(false);
        responseHeadersPane = new JTextArea();
        responseHeadersPane.setEditable(false);
        requestBodyPane = new RSyntaxTextArea();
        requestBodyPane.setEditable(false);
        requestBodyPane.setCodeFoldingEnabled(true);
        responseBodyPane = new RSyntaxTextArea();
        responseBodyPane.setEditable(false);
        responseBodyPane.setCodeFoldingEnabled(true);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Request Headers", new JScrollPane(requestHeadersPane));
        tabbedPane.addTab("Response Headers", new JScrollPane(responseHeadersPane));
        tabbedPane.addTab("Request Body", new RTextScrollPane(requestBodyPane));
        tabbedPane.addTab("Response Body", new RTextScrollPane(responseBodyPane));

        setLayout(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true,
                new JScrollPane(trafficTable), tabbedPane);
        add(splitPane, BorderLayout.CENTER);
    }

    public void setTraffic(Map<Long, ? extends HttpExchangeParser.Exchange> trafficMap)
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
        {
            requestBodyPane.setText("");
            requestBodyPane.setSyntaxEditingStyle(null);
            responseBodyPane.setText("");
            responseBodyPane.setSyntaxEditingStyle(null);
            requestHeadersPane.setText("");
            responseBodyPane.setText("");
        }
        else
        {
            requestBodyPane.setText(entry.getRequestContent());
            requestBodyPane.setSyntaxEditingStyle(entry.getRequestSyntaxStyle());
            responseBodyPane.setText(entry.getResponseContent());
            responseBodyPane.setSyntaxEditingStyle(entry.getResponseSyntaxStyle());
            requestHeadersPane.setText(Stream.of(entry.getExchange().getRequest().getHeaders()).map(Header::toString).collect(Collectors.joining("\n")));
            responseHeadersPane.setText(Stream.of(entry.getExchange().getResponse().getHeaders()).map(Header::toString).collect(Collectors.joining("\n")));

            requestBodyPane.setCaretPosition(0);
            responseBodyPane.setCaretPosition(0);
            requestHeadersPane.setCaretPosition(0);
            responseHeadersPane.setCaretPosition(0);
        }
    }

    private class TrafficTableModel extends AbstractTableModel
    {
        //Columns: ID, timestamp, address, path, HTTP method, request byte count, response byte count
        private static final List<String> COLUMN_NAMES = List.of(
                "ID", "Timestamp", "Duration", "Address", "Path", "Method", "Request Size", "Response Size"
        );

        private final List<Entry> trafficEntries;

        public TrafficTableModel(Map<Long, ? extends HttpExchangeParser.Exchange> trafficMap)
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
            return switch (columnIndex)
            {
                case 0 ->  entry.getId();
                case 1 -> entry.getTimestamp();
                case 2 -> entry.getDuration();
                case 3 -> entry.getAddress();
                case 4 -> entry.getPath();
                case 5 -> entry.getHttpMethod();
                case 6 -> entry.getRequestSize();
                case 7 -> entry.getResponseSize();
                default -> null;
            };
        }

        @Override
        public String getColumnName(int column)
        {
            return COLUMN_NAMES.get(column);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex)
        {
            return switch (columnIndex)
            {
                case 1 -> Instant.class;
                default -> super.getColumnClass(columnIndex);
            };
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

            public HttpExchangeParser.Exchange getExchange()
            {
                return exchange;
            }

            public long getId()
            {
                return id;
            }

            public Instant getTimestamp()
            {
                return exchange.getFromTime();
            }

            public Duration getDuration()
            {
                return Duration.between(exchange.getFromTime(), exchange.getToTime());
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

            public String getRequestSyntaxStyle()
            {
                return entitySyntaxStyle(exchange.getRequest().getEntity());
            }

            public String getResponseSyntaxStyle()
            {
                return entitySyntaxStyle(exchange.getResponse().getEntity());
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

                        try (InputStream content = entity.getContent(); StringWriter w = new StringWriter())
                        {
                            messageFormatterRegistry.formatter(contentType).format(contentType, content, w);
                            return w.toString();
                        }
                    }
                    catch (IOException e)
                    {
                        return e.toString();
                    }
                }
            }

            private String entitySyntaxStyle(HttpEntity entity)
            {
                if (entity == null)
                    return null;

                ContentType contentType = ContentType.parseLenient(entity.getContentType());
                return messageFormatterRegistry.formatter(contentType).getRSyntaxTextAreaStyle(contentType);
            }
        }
    }
}
