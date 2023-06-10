package au.net.causal.hymie;

import org.apache.hc.core5.http.HttpException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.SocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class TrafficRecorder
{
    private static final String REGISTRATION_KEY = "au.net.causal.hymie.TrafficRecorder";

    private final AtomicLong idCounter = new AtomicLong();

    /**
     * Keeps track of which socket object have which ID.  Does not prevent the network objects from being GCed.
     */
    private final Map<ExchangeKey, Long> networkObjectToIdMap = new ConcurrentHashMap<>();

    /**
     * Maps network object IDs to traffic.
     */
    private final Map<Long, Traffic> trafficMap = new ConcurrentHashMap<>();

    public void register()
    {
        System.getProperties().put(getRegistrationKey(), new IsolatedConsumer());
    }

    public String getRegistrationKey()
    {
        return REGISTRATION_KEY;
    }

    public Map<Long, HttpExchangeParser.Exchange> parseTraffic(boolean remove)
    {
        Map<Long, HttpExchangeParser.Exchange> parsedTraffic = new LinkedHashMap<>();
        for (Iterator<Map.Entry<Long, Traffic>> iterator = trafficMap.entrySet().iterator(); iterator.hasNext(); )
        {
            var trafficEntry = iterator.next();
            HttpExchangeParser.Exchange exchange = parseTraffic(trafficEntry.getValue());
            if (exchange != null)
            {
                parsedTraffic.put(trafficEntry.getKey(), exchange);
                if (remove)
                    iterator.remove();
            }
        }

        return parsedTraffic;
    }

    public void removeTrafficByIds(Collection<Long> ids)
    {
        trafficMap.keySet().removeAll(ids);
    }

    public synchronized void processAllTraffic(TrafficReporter reporter, Writer out)
    throws IOException
    {
        Collection<HttpExchangeParser.Exchange> parsedTraffic = parseTraffic(true).values();
        if (!parsedTraffic.isEmpty())
            reporter.report(parsedTraffic, out);
    }

    private HttpExchangeParser.Exchange parseTraffic(Traffic traffic)
    {
        //Only process if we have a complete exchange
        if (traffic.inputData.isEmpty() || traffic.outputData.isEmpty())
        {
            //System.err.println("Traffic early exit");
            return null;
        }

        try
        {
            ByteArrayOutputStream inbuf = new ByteArrayOutputStream();
            ByteArrayOutputStream outbuf = new ByteArrayOutputStream();
            for (byte[] data : traffic.outputData)
            {
                outbuf.write(data);
            }
            for (byte[] data : traffic.inputData)
            {
                inbuf.write(data);
            }

            HttpExchangeParser parser = new HttpExchangeParser();
            return parser.parse(traffic.address, traffic.fromTime, traffic.toTime, outbuf.toByteArray(), inbuf.toByteArray());
        }
        catch (IOException | HttpException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public class IsolatedConsumer implements BiConsumer<Object, Object>
    {
        private KeyIO lookUpRealKey(Object key)
        {
            //Incoming object may be one of many things
            if (key instanceof Object[] && ((Object[])key).length > 1)
            {
                Object[] compKey = (Object[])key;
                IO io = IO.valueOf((String)compKey[0]);
                Object socketObject = compKey[1];
                ExchangeKey ek = new ExchangeKey(socketObject, List.of(Arrays.copyOfRange(compKey, 2, compKey.length)));
                //System.err.println("ExchangeKey: " + ek);

                if ("sun.nio.ch.NioSocketImpl".equals(socketObject.getClass().getCanonicalName()))
                    return new KeyIO(networkObjectToIdMap.computeIfAbsent(ek, s -> idCounter.incrementAndGet()), io, socketObject.getClass().getCanonicalName());
                else if ("sun.security.ssl.SSLSocketImpl".equals(socketObject.getClass().getCanonicalName()))
                    return new KeyIO(networkObjectToIdMap.computeIfAbsent(ek, s -> idCounter.incrementAndGet()), io, socketObject.getClass().getCanonicalName());
                else if ("io.netty.handler.logging.LoggingHandler".equals(socketObject.getClass().getCanonicalName()) || "reactor.netty.transport.logging.ReactorNettyLoggingHandler".equals(socketObject.getClass().getCanonicalName()))
                    return new KeyIO(networkObjectToIdMap.computeIfAbsent(ek, s -> idCounter.incrementAndGet()), io, socketObject.getClass().getCanonicalName());
                else
                    throw new Error("Unknown socket object type: " + socketObject.getClass());
            }
            else
                throw new Error("Unknown key type: " + key.getClass());
        }

        @Override
        public void accept(Object key, Object data)
        {
            //System.err.println("Accepting data: " + Arrays.toString((Object[])key));

            KeyIO realKey = lookUpRealKey(key);

            //System.err.println("--> real key: " + realKey);

            Traffic traffic = trafficMap.computeIfAbsent(realKey.key, k -> new Traffic(realKey.getSocketObjectClassName()));

            if (data instanceof SocketAddress)
                traffic.address = (SocketAddress)data;
            else if (data instanceof byte[])
            {
                switch (realKey.io)
                {
                    case READ -> traffic.addInputData(((byte[])data).clone()); //TODO clone needed?
                    case WRITE -> traffic.addOutputData(((byte[])data).clone());
                }

                //Logging
                //System.err.println(traffic.address + " " + realKey.io + ": " + new String((byte[])data, StandardCharsets.UTF_8));
            }
        }
    }

    private static class Traffic
    {
        private static final Clock clock = Clock.systemUTC();

        private SocketAddress address;
        private final Instant fromTime;
        private Instant toTime;
        private final String socketObjectClassName;
        private final List<byte[]> inputData = new CopyOnWriteArrayList<>();
        private final List<byte[]> outputData = new CopyOnWriteArrayList<>();

        public Traffic(String socketObjectClassName)
        {
            this.socketObjectClassName = socketObjectClassName;
            this.fromTime = Instant.now(clock);
        }

        public void addInputData(byte[] data)
        {
            inputData.add(data);
            toTime = Instant.now(clock);
        }

        public void addOutputData(byte[] data)
        {
            outputData.add(data);
            toTime = Instant.now(clock);
        }
    }

    private static enum IO
    {
        READ,
        WRITE
    }

    private static class ExchangeKey
    {
        //TODO need to be weak references, unless a subkey is a string or something
        private final Object socketObject;
        private final List<Object> subKeys;

        public ExchangeKey(Object socketObject, List<Object> subKeys)
        {
            this.socketObject = socketObject;
            this.subKeys = subKeys;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExchangeKey that = (ExchangeKey) o;
            return Objects.equals(socketObject, that.socketObject) && Objects.equals(subKeys, that.subKeys);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(socketObject, subKeys);
        }

        @Override
        public String toString()
        {
            return "ExchangeKey{" +
                    "socketObject=" + socketObject +
                    ", subKeys=" + subKeys +
                    '}';
        }
    }

    private static class KeyIO
    {
        private final long key;
        private final IO io;
        private final String socketObjectClassName;

        public KeyIO(long key, IO io, String socketObjectClassName)
        {
            this.key = key;
            this.io = io;
            this.socketObjectClassName = socketObjectClassName;
        }

        public long getKey()
        {
            return key;
        }

        public IO getIo()
        {
            return io;
        }

        public String getSocketObjectClassName()
        {
            return socketObjectClassName;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            KeyIO keyIO = (KeyIO) o;
            return getKey() == keyIO.getKey() && getIo() == keyIO.getIo();
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getKey(), getIo());
        }

        @Override
        public String toString()
        {
            return "KeyIO{" +
                    "key=" + key +
                    ", io=" + io +
                    ", socketObjectClassName='" + socketObjectClassName + '\'' +
                    '}';
        }
    }
}
