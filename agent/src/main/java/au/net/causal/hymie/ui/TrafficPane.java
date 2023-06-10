package au.net.causal.hymie.ui;

import au.net.causal.hymie.HttpExchangeParser;
import au.net.causal.hymie.formatter.MessageFormatterRegistry;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static au.net.causal.hymie.ui.SimpleTableModel.Column.*;

public class TrafficPane extends JPanel
{
    private final MessageFormatterRegistry messageFormatterRegistry;

    private final JTable trafficTable;
    private TrafficTableModel trafficTableModel;

    private final JTextArea requestParametersPane;
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

        requestParametersPane = new JTextArea();
        requestParametersPane.setEditable(false);
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
        tabbedPane.addTab("Request Parameters", new JScrollPane(requestParametersPane));
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

    public Set<Long> getTrafficIds()
    {
        return trafficTableModel.getRows()
                                .stream()
                                .map(Entry::getId)
                                .collect(Collectors.toUnmodifiableSet());
    }

    private void trafficTableSelectionUpdated()
    {
        int rowIndex = trafficTable.getSelectedRow();
        if (rowIndex < 0)
            trafficTableEntrySelected(null);
        else
            trafficTableEntrySelected(trafficTableModel.getRowAt(rowIndex));
    }

    protected void trafficTableEntrySelected(Entry entry)
    {
        if (entry == null)
        {
            requestBodyPane.setText("");
            requestBodyPane.setSyntaxEditingStyle(null);
            responseBodyPane.setText("");
            responseBodyPane.setSyntaxEditingStyle(null);
            requestParametersPane.setText("");
            requestHeadersPane.setText("");
            responseHeadersPane.setText("");
        }
        else
        {
            requestBodyPane.setText(entry.getRequestContent());
            requestBodyPane.setSyntaxEditingStyle(entry.getRequestSyntaxStyle());
            responseBodyPane.setText(entry.getResponseContent());
            responseBodyPane.setSyntaxEditingStyle(entry.getResponseSyntaxStyle());
            requestParametersPane.setText(entry.getRequestUriParameters().stream().map(NameValuePair::toString).collect(Collectors.joining("\n")));
            requestHeadersPane.setText(Stream.of(entry.getExchange().getRequest().getHeaders()).map(Header::toString).collect(Collectors.joining("\n")));
            responseHeadersPane.setText(Stream.of(entry.getExchange().getResponse().getHeaders()).map(Header::toString).collect(Collectors.joining("\n")));

            requestBodyPane.setCaretPosition(0);
            responseBodyPane.setCaretPosition(0);
            requestHeadersPane.setCaretPosition(0);
            responseHeadersPane.setCaretPosition(0);
        }
    }

    private class TrafficTableModel extends SimpleTableModel<Entry>
    {
        private static final List<Column<Entry, ?>> columns = List.of(
            column("ID", Long.class, Entry::getId),
            column("Timestamp", Instant.class, Entry::getTimestamp),
            column("Duration", Duration.class, Entry::getDuration),
            column("Address", Entry::getAddress),
            column("Path", Entry::getPath),
            column("Method", Entry::getHttpMethod),
            column("Status", Entry::getResponseStatusCode),
            column("Request Size", Long.class, Entry::getRequestSize),
            column("Response Size", Long.class, Entry::getResponseSize)
        );

        public TrafficTableModel(Map<Long, ? extends HttpExchangeParser.Exchange> trafficMap)
        {
            super(columns, trafficMap.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());
        }
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

        public String getResponseStatusCode()
        {
            return String.valueOf(exchange.getResponse().getCode());
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

        public List<NameValuePair> getRequestUriParameters()
        {
            try
            {
                List<NameValuePair> result = WWWFormCodec.parse(exchange.getRequest().getUri().getQuery(), StandardCharsets.UTF_8);
                if (result == null)
                    result = List.of();

                return result;
            }
            catch (URISyntaxException e)
            {
                return List.of();
            }
        }
    }
}
