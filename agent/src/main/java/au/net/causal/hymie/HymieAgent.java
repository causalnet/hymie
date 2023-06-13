package au.net.causal.hymie;

import au.net.causal.hymie.formatter.HtmlMessageFormatter;
import au.net.causal.hymie.formatter.JsonMessageFormatter;
import au.net.causal.hymie.formatter.MessageFormatterRegistry;
import au.net.causal.hymie.formatter.PlainMessageFormatter;
import au.net.causal.hymie.json.JsonTrafficReporter;
import au.net.causal.hymie.ui.HymiePane;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class HymieAgent
{
    private final Args args;
    private final TrafficRecorder trafficRecorder = new TrafficRecorder();

    private static final MessageFormatterRegistry formatterRegistry = new MessageFormatterRegistry(
            List.of(
                new JsonMessageFormatter(),
                new HtmlMessageFormatter()
            ),
            new PlainMessageFormatter()
    );

    public static void agentmain(String agentArgs, Instrumentation inst)
    {
        premain(agentArgs, inst);

        Set<String> knownClassNames = Set.of(
            "sun.nio.ch.NioSocketImpl",
            "sun.security.ssl.SSLSocketImpl$AppInputStream",
            "sun.security.ssl.SSLSocketImpl$AppOutputStream",
            "org.springframework.http.client.reactive.ReactorClientHttpConnector",
            "io.netty.handler.logging.LoggingHandler"
        );
        List<Class<?>> classesToRetransform = new ArrayList<>();
        for (Class<?> loadedClass : inst.getAllLoadedClasses())
        {
            if (knownClassNames.contains(loadedClass.getName()))
                classesToRetransform.add(loadedClass);
        }

        try
        {
            inst.retransformClasses(classesToRetransform.toArray(Class[]::new));
        }
        catch (UnmodifiableClassException e)
        {
            e.printStackTrace();
        }
    }

    public static void premain(String agentArgs, Instrumentation inst)
    {
        //Parse the args
        Args args;
        try
        {
            args = Args.parse(agentArgs);
        }
        catch (ParseException e)
        {
            System.err.println("Error parsing Hymie agent args - agent will be disabled: " + e);
            e.printStackTrace();
            return;
        }

        ExceptionalSupplier<Writer, IOException> dumpOut;
        if (args.file != null)
        {
            try
            {
                Files.write(args.file, new byte[0]);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            dumpOut = () -> Files.newBufferedWriter(args.file, StandardOpenOption.APPEND);
        }
        else
            dumpOut = () -> new CloseShieldedPrintWriter(System.err);

        HymieAgent agent = new HymieAgent(args);
        agent.run(inst);

        switch (args.mode)
        {

            case DUMP ->
            {
                if (args.dumpInterval != null)
                {
                    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                    scheduler.scheduleAtFixedRate(() -> agent.dumpSafely(dumpOut), 0L, args.dumpInterval.toMillis(), TimeUnit.MILLISECONDS);
                }

                Runtime.getRuntime().addShutdownHook(new Thread(() ->
                {
                    agent.dumpSafely(dumpOut);
                }));
            }
            case UI ->
            {
                //If the user really wants a UI, do our best to disable headless mode
                //IntelliJ/Spring can turn headless mode on in its runners
                //If UI has already been initialized elsewhere and headless mode is active then this won't work, but try our best
                if ("true".equals(System.getProperty("java.awt.headless")))
                    System.setProperty("java.awt.headless", "false");

                HymiePane pane = new HymiePane(agent.trafficRecorder, formatterRegistry);
                JFrame frame = new JFrame("Hymie");
                frame.add(pane, BorderLayout.CENTER);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(1000, 1000);
                frame.setVisible(true);
            }
        }
    }

    public HymieAgent(Args args)
    {
        this.args = args;
    }

    public void dump(Writer out)
    throws IOException
    {
        trafficRecorder.processAllTraffic(args.format.getReporter(), out);
    }

    public void dumpSafely(ExceptionalSupplier<Writer, IOException> out)
    {
        try
        {
            try (Writer w = out.get())
            {
                dump(w);
            }
        }
        catch (IOException e)
        {
            System.err.println("Hymie encountered an error dumping network traffic: " + e);
            e.printStackTrace();
        }
    }

    public void run(Instrumentation inst)
    {
        //Install our hacks into system properties
        trafficRecorder.register();

        //Add transformers
        inst.addTransformer(new ClassFileTransformer()
        {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException
            {
                try
                {
                    if ("sun/nio/ch/NioSocketImpl".equals(className))
                    {
                        ClassPool classPool = new ClassPool(null);
                        //classPool.appendSystemPath(); This one has problems is class context classloader is not set on the current thread
                        classPool.appendClassPath(new LoaderClassPath(this.getClass().getClassLoader()));
                        classPool.appendClassPath(new LoaderClassPath(loader));

                        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

                        CtMethod readMethod = ctClass.getDeclaredMethod("read");
                        transformNioSocketImplReadMethod(readMethod);
                        CtMethod writeMethod = ctClass.getDeclaredMethod("write");
                        transformNioSocketImplWriteMethod(writeMethod);

                        classfileBuffer = ctClass.toBytecode();
                        ctClass.detach();
                    }

                    else if ("sun/security/ssl/SSLSocketImpl$AppInputStream".equals(className))
                    {
                        ClassPool classPool = new ClassPool(null);
                        //classPool.appendSystemPath(); This one has problems is class context classloader is not set on the current thread
                        classPool.appendClassPath(new LoaderClassPath(this.getClass().getClassLoader()));
                        classPool.appendClassPath(new LoaderClassPath(loader));

                        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

                        CtMethod readMethod = Arrays.stream(ctClass.getDeclaredMethods("read")).filter(m -> parameterCount(m) == 3).findFirst().get();
                        transformSslSocketAppInputStreamReadMethod(readMethod);

                        classfileBuffer = ctClass.toBytecode();
                        ctClass.detach();
                    }

                    else if ("sun/security/ssl/SSLSocketImpl$AppOutputStream".equals(className))
                    {
                        ClassPool classPool = new ClassPool(null);
                        //classPool.appendSystemPath(); This one has problems is class context classloader is not set on the current thread
                        classPool.appendClassPath(new LoaderClassPath(this.getClass().getClassLoader()));
                        classPool.appendClassPath(new LoaderClassPath(loader));

                        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

                        CtMethod writeMethod = Arrays.stream(ctClass.getDeclaredMethods("write")).filter(m -> parameterCount(m) == 3).findFirst().get();
                        transformSslSocketAppOutputStreamWriteMethod(writeMethod);

                        classfileBuffer = ctClass.toBytecode();
                        ctClass.detach();
                    }

                    else if ("org/springframework/http/client/reactive/ReactorClientHttpConnector".equals(className))
                    {
                        ClassPool classPool = new ClassPool(null);
                        //classPool.appendSystemPath(); This one has problems is class context classloader is not set on the current thread
                        classPool.appendClassPath(new LoaderClassPath(this.getClass().getClassLoader()));
                        classPool.appendClassPath(new LoaderClassPath(loader));

                        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

                        //Don't do this for now since it only works if agent is there at construction
                        /*
                        CtConstructor init = Arrays.stream(ctClass.getConstructors()).filter(m -> parameterCount(m) == 1).findFirst().get();
                        transformReactorClientHttpConnectorConstructor(init);
                         */

                        //This approach allows agent to attach at any time and it will just work
                        CtMethod connectMethod = ctClass.getDeclaredMethod("connect");
                        transformReactorClientHttpConnectorConnectMethod(connectMethod);

                        classfileBuffer = ctClass.toBytecode();
                        ctClass.detach();
                    }

                    else if ("io/netty/handler/logging/LoggingHandler".equals(className))
                    {
                        ClassPool classPool = new ClassPool(null);
                        //classPool.appendSystemPath(); This one has problems is class context classloader is not set on the current thread
                        classPool.appendClassPath(new LoaderClassPath(this.getClass().getClassLoader()));
                        classPool.appendClassPath(new LoaderClassPath(loader));

                        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

                        CtMethod readMethod = ctClass.getDeclaredMethod("channelRead");
                        transformLoggingHandlerReadMethod(readMethod);
                        CtMethod writeMethod = ctClass.getDeclaredMethod("write");
                        transformLoggingHandlerWriteMethod(writeMethod);

                        classfileBuffer = ctClass.toBytecode();
                        ctClass.detach();
                    }
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
                return classfileBuffer;
            }
        }, true);
    }

    private int parameterCount(CtBehavior method)
    {
        try
        {
            return method.getParameterTypes().length;
        }
        catch (NotFoundException e)
        {
            e.printStackTrace();
            return -1;
        }

    }

    private void transformNioSocketImplReadMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertAfter("""
            
            if ($0.port == 80 || $0.port == 8080)
            {
                byte[] b = $1;
                int offset = $2;
                int length = $3;
                int n = $_;
                
                if (n > 0)
                {
                    java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                    Object[] key = new Object[] {"READ", $0};
                    c.accept(key, new java.net.InetSocketAddress($0.address, $0.port));
                    c.accept(key, java.util.Arrays.copyOfRange(b, offset, offset + n));
                    //System.err.println("received(" + $0.port + "): " + new String(b, offset, n));
                }
            }
        """);
    }

    private void transformSslSocketAppInputStreamReadMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertAfter("""
            if (this$0.getPort() == 443 || this$0.getPort() == 8443)
            {
                byte[] b = $1;
                int offset = $2;
                int length = $3;
                int n = $_;
                
                if (n > 0)
                {
                    //System.err.println("SSLreceived(" + this$0.getPort() + "): " + new String(b, offset, n));
                    java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                    Object[] key = new Object[] {"READ", this$0};
                    c.accept(key, new java.net.InetSocketAddress(this$0.getInetAddress(), this$0.getPort()));
                    c.accept(key, java.util.Arrays.copyOfRange(b, offset, offset + n));
                }
            }
        """);
    }

    private void transformNioSocketImplWriteMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertAfter("""
            if ($0.port == 80 || $0.port == 8080)
            {
                byte[] b = $1;
                int offset = $2;
                int length = $3;
                //System.err.println("sent(" + $0.port + "," + $0 + "): " + new String(b, offset, length));
                java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                Object[] key = new Object[] {"WRITE", $0};
                c.accept(key, new java.net.InetSocketAddress($0.address, $0.port));
                c.accept(key, java.util.Arrays.copyOfRange(b, offset, offset + length));
            }
        """);
    }

    private void transformSslSocketAppOutputStreamWriteMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertAfter("""
            if (this$0.getPort() == 443 || this$0.getPort() == 8443)
            {
                byte[] b = $1;
                int offset = $2;
                int length = $3;
                //System.err.println("SSLsent(" + this$0.getPort() + "," + this$0 + "," + this + "): " + new String(b, offset, length));
                java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                Object[] key = new Object[] {"WRITE", this$0};
                c.accept(key, new java.net.InetSocketAddress(this$0.getInetAddress(), this$0.getPort()));
                c.accept(key, java.util.Arrays.copyOfRange(b, offset, offset + length));
            }
        """);
    }

    /*
    private void transformReactorClientHttpConnectorConstructor(CtConstructor m)
    throws CannotCompileException
    {
        m.insertBeforeBody("""
            $1 = (reactor.netty.http.client.HttpClient)($1.wiretap("au.net.causal.hymie", io.netty.handler.logging.LogLevel.TRACE));
        """);
    }
     */

    private void transformReactorClientHttpConnectorConnectMethod(CtMethod m)
    throws CannotCompileException
    {
        m.instrument(new ExprEditor()
        {
            @Override
            public void edit(FieldAccess f) throws CannotCompileException
            {
                if ("httpClient".equals(f.getFieldName()))
                {
                    f.replace("""
                        $_ = (reactor.netty.http.client.HttpClient)($0.httpClient.wiretap("au.net.causal.hymie", io.netty.handler.logging.LogLevel.TRACE));
                    """);
                }
            }
        });
    }

    private void transformLoggingHandlerReadMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertBefore("""
                java.net.InetSocketAddress address = (java.net.InetSocketAddress)$1.channel().remoteAddress();
                if (address.getPort() == 443 || address.getPort() == 8443 || address.getPort() == 80 || address.getPort() == 8080)
                {
                    if (msg instanceof io.netty.buffer.ByteBuf)
                    {
                        io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf)$2;
                       
                        byte[] data = new byte[buf.readableBytes()];
                        buf.getBytes(buf.readerIndex(), data);
                        java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                        Object[] key = new Object[] {"READ", $0, $1.channel().id().asLongText(), $1.channel().attr(io.netty.util.AttributeKey.valueOf("$CONNECTION")).get()};
                        c.accept(key, address);
                        c.accept(key, data);
                       
                        //String s = buf.toString(java.nio.charset.StandardCharsets.UTF_8);
                        //System.err.println("nettyReceived(" + address.getPort() + "): " + s);
                    }
                }
        """);
    }

    private void transformLoggingHandlerWriteMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertBefore("""
                java.net.InetSocketAddress address = (java.net.InetSocketAddress)$1.channel().remoteAddress();
                if (address.getPort() == 443 || address.getPort() == 8443 || address.getPort() == 80 || address.getPort() == 8080)
                {
                    if (msg instanceof io.netty.buffer.ByteBuf)
                    {
                        io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf)$2;
                       
                        byte[] data = new byte[buf.readableBytes()];
                        buf.getBytes(buf.readerIndex(), data);
                        java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                        Object[] key = new Object[] {"WRITE", $0, $1.channel().id().asLongText(), $1.channel().attr(io.netty.util.AttributeKey.valueOf("$CONNECTION")).get()};
                        c.accept(key, address);
                        c.accept(key, data);

                       //String s = buf.toString(java.nio.charset.StandardCharsets.UTF_8);
                       //System.err.println("nettySent(" + address.getPort() + "): " + s);
                    }
                }
        """);
    }

    public static class Args
    {
        private Format format = Format.PLAIN;
        private Path file;
        private Duration dumpInterval;
        private Mode mode = Mode.DUMP;

        private Args()
        {
        }

        public static Args parse(String s)
        throws ParseException
        {
            Args args = new Args();

            if (s != null)
            {
                for (String part : s.split(Pattern.quote(",")))
                {
                    String[] keyValue = part.split(Pattern.quote("="), 2);
                    if (keyValue.length < 2)
                        throw new ParseException("Error parsing arg part: " + part, 0);

                    String key = keyValue[0];
                    String value = keyValue[1];

                    switch (key)
                    {
                        case "format" -> args.format = Format.valueOf(value.toUpperCase(Locale.ROOT));
                        case "file" -> args.file = Path.of(value);
                        case "dumpInterval" -> args.dumpInterval = Duration.parse(value);
                        case "mode" -> args.mode = Mode.valueOf(value.toUpperCase(Locale.ROOT));
                        default -> throw new ParseException("Unknown arg: " + key, 0);
                    }
                }
            }

            return args;
        }

        public static enum Format
        {
            PLAIN(new PlainTrafficReporter()),
            PLAIN_FORMATTED(new PlainFormattedTrafficReporter()),
            JSON(new JsonTrafficReporter());

            private final TrafficReporter reporter;

            private Format(TrafficReporter reporter)
            {
                this.reporter = reporter;
            }

            public TrafficReporter getReporter()
            {
                return reporter;
            }
        }

        public static enum Mode
        {
            DUMP,
            UI
        }
    }

    private static class CloseShieldedPrintWriter extends PrintWriter
    {
        public CloseShieldedPrintWriter(OutputStream out)
        {
            super(out);
        }

        @Override
        public void close()
        {
            //Intentionally do nothing, but do a flush at least
            flush();
        }
    }
}
