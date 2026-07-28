package su.knst.crypto.command;

import org.jline.utils.AttributedStyle;
import su.knst.crypto.utils.ConsoleBox;

public class Panel {
    protected final String title;
    protected final boolean framed;
    protected final AttributedStyle style;
    protected final String content;

    protected Panel(String title, boolean framed, AttributedStyle style, String content) {
        this.title = title;
        this.framed = framed;
        this.style = style;
        this.content = content;
    }

    public static Panel framed(String title, String content, AttributedStyle style) {
        return new Panel(title, true, style, content);
    }

    public static Panel plain(String content) {
        return new Panel(null, false, null, content);
    }

    public String render(boolean inheritedError) {
        if (!framed)
            return content == null ? "" : content;

        AttributedStyle s = style != null
                ? style
                : (inheritedError ? AttributedStyle.DEFAULT.foreground(AttributedStyle.RED) : AttributedStyle.DEFAULT);

        return ConsoleBox.box(title != null ? title : "", ConsoleBox.splitTrimmed(content), s);
    }

    public String plainText() {
        return content == null ? "" : content;
    }
}
