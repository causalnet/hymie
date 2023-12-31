package au.net.causal.hymie.ui;

import au.net.causal.hymie.HttpExchangeParser;
import au.net.causal.hymie.TrafficRecorder;
import au.net.causal.hymie.formatter.MessageFormatterRegistry;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Collection;
import java.util.List;

public class HymiePane extends JPanel
{
    private final TrafficPane trafficPane;

    public HymiePane(TrafficRecorder trafficRecorder, MessageFormatterRegistry messageFormatterRegistry)
    {
        trafficPane = new TrafficPane(messageFormatterRegistry);

        setLayout(new BorderLayout());
        add(trafficPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        add(bottomPanel, BorderLayout.SOUTH);
        JButton reloadButton = new JButton("Reload");
        JButton clearButton = new JButton("Clear");
        bottomPanel.add(reloadButton);
        bottomPanel.add(clearButton);
        reloadButton.addActionListener(ev ->
        {
            Collection<HttpExchangeParser.Exchange> traffic = trafficRecorder.parseTraffic(false);
            trafficPane.setTraffic(traffic);
        });
        clearButton.addActionListener(ev ->
        {
            trafficRecorder.removeTrafficByIds(trafficPane.getTrafficIds());
            trafficPane.setTraffic(List.of());
        });
    }
}
