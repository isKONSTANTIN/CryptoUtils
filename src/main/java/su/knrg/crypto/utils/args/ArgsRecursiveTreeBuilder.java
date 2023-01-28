package su.knrg.crypto.utils.args;

import org.jline.builtins.Completers;
import org.jline.builtins.Completers.TreeCompleter.Node;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jline.builtins.Completers.TreeCompleter.node;

public class ArgsRecursiveTreeBuilder extends ArgsTreeBuilder {

    protected ArgsRecursiveTreeBuilder(ArgsTreeBuilder parent) {
        super(parent);
    }

    @Override
    public Node build() {
        Node lastNode = null;
        Collections.reverse(candidates);

        for (Object object : candidates) {
            if (object instanceof ArgsTreeBuilder)
                object = ((ArgsTreeBuilder)object).build();

            if (lastNode == null) {
                lastNode = object instanceof Node ? (Node) object : node(object);
            }else {
                lastNode = node(object, lastNode);
            }
        }

        return lastNode;
    }
}
