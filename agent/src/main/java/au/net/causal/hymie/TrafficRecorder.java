package au.net.causal.hymie;

import java.lang.reflect.Field;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
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
    private final Map<Object, Long> networkObjectToIdMap = Collections.synchronizedMap(new WeakHashMap<>());

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

    public class IsolatedConsumer implements BiConsumer<Object, Object>
    {
        private KeyIO lookUpRealKey(Object key)
        {
            //Incoming object may be one of many things
            /*
            if ("sun.security.ssl.SSLSocketImpl.AppInputStream".equals(key.getClass().getCanonicalName()))
            {
                Object socketObject = readOuterClassObject(key);
                return new KeyIO(networkObjectToIdMap.computeIfAbsent(socketObject, s -> idCounter.incrementAndGet()), IO.READ);
            }
            else if ("sun.security.ssl.SSLSocketImpl.AppOutputStream".equals(key.getClass().getCanonicalName()))
            {
                Object socketObject = readOuterClassObject(key);
                return new KeyIO(networkObjectToIdMap.computeIfAbsent(socketObject, s -> idCounter.incrementAndGet()), IO.WRITE);
            }
             */
            if (key instanceof Object[] && ((Object[])key).length == 2)
            {
                Object[] compKey = (Object[])key;
                Object socketObject = compKey[0];
                IO io = IO.valueOf((String)compKey[1]);

                if ("sun.nio.ch.NioSocketImpl".equals(socketObject.getClass().getCanonicalName()))
                    return new KeyIO(networkObjectToIdMap.computeIfAbsent(socketObject, s -> idCounter.incrementAndGet()), io);
                else if ("sun.security.ssl.SSLSocketImpl".equals(socketObject.getClass().getCanonicalName()))
                    return new KeyIO(networkObjectToIdMap.computeIfAbsent(socketObject, s -> idCounter.incrementAndGet()), io);
                else if ("io.netty.handler.logging.LoggingHandler".equals(socketObject.getClass().getCanonicalName()))
                    return new KeyIO(networkObjectToIdMap.computeIfAbsent(socketObject, s -> idCounter.incrementAndGet()), io);
                else
                    throw new Error("Unknown socket object type: " + socketObject.getClass());
            }
            else
                throw new Error("Unknown key type: " + key.getClass());
        }

        @Override
        public void accept(Object key, Object data)
        {
            KeyIO realKey = lookUpRealKey(key);
            Traffic traffic = trafficMap.computeIfAbsent(realKey.key, k -> new Traffic());

            if (data instanceof SocketAddress)
                traffic.address = (SocketAddress)data;
            else if (data instanceof byte[])
            {
                switch (realKey.io)
                {
                    case READ -> traffic.inputData.add(((byte[])data).clone()); //TODO clone needed?
                    case WRITE -> traffic.outputData.add(((byte[])data).clone());
                }

                //Logging
                System.err.println(traffic.address + " " + realKey.io + ": " + new String((byte[])data, StandardCharsets.UTF_8));
            }
        }
    }

    private static class Traffic
    {
        private SocketAddress address;
        private final List<byte[]> inputData = new CopyOnWriteArrayList<>();
        private final List<byte[]> outputData = new CopyOnWriteArrayList<>();
    }

    private static enum IO
    {
        READ,
        WRITE
    }

    private static class KeyIO
    {
        private final long key;
        private final IO io;

        public KeyIO(long key, IO io)
        {
            this.key = key;
            this.io = io;
        }

        public long getKey()
        {
            return key;
        }

        public IO getIo()
        {
            return io;
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
    }
}
