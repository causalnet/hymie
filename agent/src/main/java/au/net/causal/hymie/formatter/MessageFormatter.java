package au.net.causal.hymie.formatter;

import org.apache.hc.core5.http.ContentType;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public interface MessageFormatter
{
    public void format(ContentType contentType, InputStream data, Writer formatted)
    throws IOException;

    public boolean supportsContentType(ContentType contentType);

    /**
     * Gets the style name for the message usable by the RSyntaxTextArea component that can be used for messages of this type.
     *
     * @param contentType content type.
     *
     * @return the style name, or null if no special style should be used.
     *
     * @see SyntaxConstants
     */
    public String getRSyntaxTextAreaStyle(ContentType contentType);
}
