package au.net.causal.hymie.it;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.TestPropertySourceUtils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = AbstractSslNetworkExerciser.PropertyOverrideContextInitializer.class)
@AutoConfigureMockMvc
public abstract class AbstractSslNetworkExerciser
{
    private static final String KEY_ALIAS = "main";
    private static final String KEY_PASSWORD = "changeit";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static Path keyStoreFile;

    @LocalServerPort
    protected int serverPort;

    @BeforeAll
    static void setUpSsl(@TempDir Path tempDir)
    throws Exception
    {
        generateKeyStore(tempDir);
    }

    private static void generateKeyStore(Path tempDir)
    throws Exception
    {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();

        Certificate[] chain = {selfSignedCertificate.cert()};

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry(KEY_ALIAS, selfSignedCertificate.key(), KEY_PASSWORD.toCharArray(), chain);

        keyStoreFile = Files.createTempFile(tempDir, "keystore", ".jks");

        try (OutputStream os = Files.newOutputStream(keyStoreFile))
        {
            keyStore.store(os, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    static class PropertyOverrideContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext>
    {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext)
        {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
                                                                      "server.port=8443",
                                                                      "server.ssl.key-store=" + keyStoreFile.toUri(),
                                                                      "server.ssl.key-store-type=JKS",
                                                                      "server.ssl.enabled=true",
                                                                      "server.ssl.key-alias=" + KEY_ALIAS,
                                                                      "server.ssl.key-store-password=" + KEYSTORE_PASSWORD,
                                                                      "server.ssl.key-password=" + KEY_PASSWORD
            );
        }
    }

    protected void configureSsl(URLConnection c)
    {
        if (c instanceof HttpsURLConnection)
        {
            HttpsURLConnection hc = (HttpsURLConnection)c;
            hc.setHostnameVerifier((hostname, session) -> true);

            TrustManager[] trustManagers = new TrustManager[] {new HttpsTrustEverythingManager()};

            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustManagers, new SecureRandom());
                hc.setSSLSocketFactory(context.getSocketFactory());
            }
            catch (NoSuchAlgorithmException | KeyManagementException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private static class HttpsTrustEverythingManager implements X509TrustManager
    {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
        throws CertificateException
        {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
        throws CertificateException
        {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers()
        {
            return new X509Certificate[0];
        }
    }

    public static class TomcatSslCustomizer implements TomcatConnectorCustomizer
    {
        @Override
        public void customize(Connector connector)
        {
            ProtocolHandler handler = connector.getProtocolHandler();
            if (handler instanceof AbstractHttp11JsseProtocol<?>)
                configureSsl((AbstractHttp11JsseProtocol<?>)handler);
        }

        private void configureSsl(AbstractHttp11JsseProtocol<?> handler)
        {
            for (SSLHostConfig sslHostConfig : handler.findSslHostConfigs())
            {
                for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates())
                {
                    //Functionally doesn't make a difference since the store itself is set but is useful for Tomcat logging
                    certificate.setCertificateKeystoreFile(keyStoreFile.toString());
                }
            }
        }
    }
}
