package su.knst.crypto.utils.args;

import org.jline.builtins.Completers.TreeCompleter.Node;

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
            if (lastNode == null) {
                if (object instanceof ArgsTreeBuilder)
                    object = ((ArgsTreeBuilder)object).build();

                lastNode = object instanceof Node ? (Node) object : node(object);

                continue;
            }

            if (object instanceof ArgsTreeBuilder builder) {
                List<Node> nodes = builder.getNodes();
                nodes.add(lastNode);

                lastNode = new Node(builder.getCandidatesAsCompleter(), nodes);
            }else{
                lastNode = node(object, lastNode);
            }
        }

        return lastNode;
    }
}
