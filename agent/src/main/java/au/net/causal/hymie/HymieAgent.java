package au.net.causal.hymie;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;

public class HymieAgent
{
    private final TrafficRecorder trafficRecorder = new TrafficRecorder();

    public static void premain(String agentArgs, Instrumentation inst)
    {
        System.out.println("Hello from Hymie agent");

        HymieAgent agent = new HymieAgent();
        agent.run(inst);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            System.err.println("End run:");
            agent.trafficRecorder.processAllTraffic();
        }));
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
                        classPool.appendSystemPath();
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
                        classPool.appendSystemPath();
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
                        classPool.appendSystemPath();
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
                        classPool.appendSystemPath();
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
                        classPool.appendSystemPath();
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
        });
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
                    Object[] key = new Object[] {$0, "READ"};
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
                    Object[] key = new Object[] {this$0, "READ"};
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
                //System.err.println("sent(" + $0.port + "): " + new String(b, offset, length));
                java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                Object[] key = new Object[] {$0, "WRITE"};
                c.accept(key, new java.net.InetSocketAddress($0.address, $0.port));
                c.accept(key, java.util.Arrays.copyOfRange(b, length, offset + length));
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
                //System.err.println("SSLsent(" + this$0.getPort() + "): " + new String(b, offset, length));
                java.util.function.BiConsumer c = (java.util.function.BiConsumer)System.getProperties().get("au.net.causal.hymie.TrafficRecorder");
                Object[] key = new Object[] {this$0, "WRITE"};
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
                        Object[] key = new Object[] {$0, "READ"};
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
                        Object[] key = new Object[] {$0, "WRITE"};
                        c.accept(key, address);
                        c.accept(key, data);

                       //String s = buf.toString(java.nio.charset.StandardCharsets.UTF_8);
                       //System.err.println("nettySent(" + address.getPort() + "): " + s);
                    }
                }
        """);
    }
}
