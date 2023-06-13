package au.net.causal.hymie;

import org.apache.hc.core5.http.HttpException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.net.SocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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

    public Collection<HttpExchangeParser.Exchange> parseTraffic(boolean remove)
    {
        List<HttpExchangeParser.Exchange> parsedTraffic = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Traffic>> iterator = trafficMap.entrySet().iterator(); iterator.hasNext(); )
        {
            var trafficEntry = iterator.next();
            List<HttpExchangeParser.Exchange> exchanges = parseTraffic(trafficEntry.getKey(), trafficEntry.getValue());
            parsedTraffic.addAll(exchanges);
            if (!exchanges.isEmpty() && remove)
                iterator.remove();
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
        Collection<HttpExchangeParser.Exchange> parsedTraffic = parseTraffic(true);
        if (!parsedTraffic.isEmpty())
            reporter.report(parsedTraffic, out);
    }

    private List<RawExchange> parseRawExchangesFromTraffic(Traffic traffic)
    {
        //Read out/in/out/in/...
        IO mode = IO.WRITE;

        List<RawExchange> rawExchanges = new ArrayList<>();
        ByteArrayOutputStream inbuf = new ByteArrayOutputStream();
        ByteArrayOutputStream outbuf = new ByteArrayOutputStream();

        try
        {
            for (var fragment : traffic.fragments)
            {
                if (fragment.io != mode && inbuf.size() > 0 && outbuf.size() > 0)
                {
                    rawExchanges.add(new RawExchange(inbuf.toByteArray(), outbuf.toByteArray()));
                    inbuf = new ByteArrayOutputStream();
                    outbuf = new ByteArrayOutputStream();
                }
                mode = fragment.io;
                switch (fragment.io)
                {
                    case READ -> inbuf.write(fragment.data);
                    case WRITE -> outbuf.write(fragment.data);
                }
            }

            //Flush remaining record
            if (inbuf.size() > 0 && outbuf.size() > 0)
                rawExchanges.add(new RawExchange(inbuf.toByteArray(), outbuf.toByteArray()));
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return List.of();
        }

        return rawExchanges;
    }

    private List<HttpExchangeParser.Exchange> parseTraffic(long connectionId, Traffic traffic)
    {
        List<RawExchange> rawExchanges = parseRawExchangesFromTraffic(traffic);
        List<HttpExchangeParser.Exchange> parsedExchanges = new ArrayList<>(rawExchanges.size());

        //Now we have multiple raw exchanges
        //Parse them
        for (RawExchange rawExchange : rawExchanges)
        {
            HttpExchangeParser parser = new HttpExchangeParser();
            try
            {
                HttpExchangeParser.Exchange parsed = parser.parse(connectionId, traffic.address, traffic.fromTime, traffic.toTime, rawExchange.outputData, rawExchange.inputData);
                parsedExchanges.add(parsed);
            }
            catch (IOException | HttpException e)
            {
                e.printStackTrace();
                //Ignore
            }
        }

        return parsedExchanges;
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
                //TODO write/read/write should be split to multiple exchanges
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

        private final List<Fragment> fragments = new CopyOnWriteArrayList<>();

        //private final List<byte[]> inputData = new CopyOnWriteArrayList<>();
        //private final List<byte[]> outputData = new CopyOnWriteArrayList<>();

        public Traffic(String socketObjectClassName)
        {
            this.socketObjectClassName = socketObjectClassName;
            this.fromTime = Instant.now(clock);
        }

        public void addInputData(byte[] data)
        {
            //inputData.add(data);
            fragments.add(new Fragment(IO.READ, data));
            toTime = Instant.now(clock);
        }

        public void addOutputData(byte[] data)
        {
            //outputData.add(data);
            fragments.add(new Fragment(IO.WRITE, data));
            toTime = Instant.now(clock);
        }
    }

    private static class Fragment
    {
        private final IO io;
        private final byte[] data;

        public Fragment(IO io, byte[] data)
        {
            this.io = io;
            this.data = data;
        }
    }

    private static class RawExchange
    {
        private final byte[] inputData;
        private final byte[] outputData;

        public RawExchange(byte[] inputData, byte[] outputData)
        {
            this.inputData = inputData;
            this.outputData = outputData;
        }
    }

    private static enum IO
    {
        READ,
        WRITE
    }

    private static class ExchangeKey
    {
        private final WeakReference<Object> socketObject;
        private final List<Object> subKeys;

        public ExchangeKey(Object socketObject, List<Object> subKeys)
        {
            this.socketObject = new WeakReference<>(socketObject);
            this.subKeys = subKeys.stream().map(ExchangeKey::weaklyReference).toList();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExchangeKey that = (ExchangeKey) o;
            return Objects.equals(socketObject.get(), that.socketObject.get()) && Objects.equals(subKeysDereferenced(), that.subKeysDereferenced());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(socketObject.get(), subKeysDereferenced());
        }

        private List<?> subKeysDereferenced()
        {
            return subKeys.stream().map(ExchangeKey::dereference).collect(Collectors.toList());
        }

        private static Object dereference(Object a)
        {
            if (a instanceof WeakReference<?>)
                return ((WeakReference<?>)a).get();
            else
                return a;
        }

        private static Object weaklyReference(Object a)
        {
            if (a instanceof String || a instanceof Number || a instanceof Enum<?>) //String and numbers
                return a;
            else
                return new WeakReference<>(a);
        }

        @Override
        public String toString()
        {
            return "ExchangeKey{" +
                    "socketObject=" + socketObject.get() +
                    ", subKeys=" + subKeysDereferenced() +
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
