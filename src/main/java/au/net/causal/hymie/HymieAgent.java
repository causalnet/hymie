package au.net.causal.hymie;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;

public class HymieAgent
{
    public static void premain(String agentArgs, Instrumentation inst)
    {
        System.out.println("Hello from Hymie agent");

        HymieAgent agent = new HymieAgent();
        agent.run(inst);
    }

    public void run(Instrumentation inst)
    {
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
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
                return classfileBuffer;
            }
        });
    }

    private int parameterCount(CtMethod method)
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
            byte[] b = $1;
            int offset = $2;
            int length = $3;
            int n = $_;
            
            if (n > 0)
            {
                java.lang.System.err.println("received: " + new String(b, offset, n));
            }
        """);
    }

    private void transformSslSocketAppInputStreamReadMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertAfter("""
            byte[] b = $1;
            int offset = $2;
            int length = $3;
            int n = $_;
            
            if (n > 0)
            {
                java.lang.System.err.println("SSLreceived: " + new String(b, offset, n));
            }
        """);
    }

    private void transformNioSocketImplWriteMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertAfter("""
            byte[] b = $1;
            int offset = $2;
            int length = $3;
            java.lang.System.err.println("sent: " + new String(b, offset, length));
        """);
    }

    private void transformSslSocketAppOutputStreamWriteMethod(CtMethod m)
    throws CannotCompileException
    {
        m.insertAfter("""
            byte[] b = $1;
            int offset = $2;
            int length = $3;
            java.lang.System.err.println("SSLsent: " + new String(b, offset, length));
        """);
    }
}
