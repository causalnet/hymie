package au.net.causal.hymie.formatter;

import org.apache.hc.core5.http.ContentType;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public class HtmlMessageFormatter extends PlainMessageFormatter
{
    @Override
    public boolean supportsContentType(ContentType contentType)
    {
        return ContentType.TEXT_HTML.isSameMimeType(contentType);
    }

    @Override
    public String getRSyntaxTextAreaStyle(ContentType contentType)
    {
        return SyntaxConstants.SYNTAX_STYLE_HTML;
    }
}
