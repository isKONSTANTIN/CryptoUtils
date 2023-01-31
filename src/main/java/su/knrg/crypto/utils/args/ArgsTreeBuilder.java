package su.knrg.crypto.utils.args;

import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ArgsTreeBuilder {

    protected ArgsTreeBuilder parent;
    protected ArrayList<Object> candidates = new ArrayList<>();
    protected Completer completer;

    public static ArgsTreeBuilder builder() {
        return new ArgsTreeBuilder();
    }

    public ArgsTreeBuilder() {

    }

    protected ArgsTreeBuilder(ArgsTreeBuilder parent) {
        this.parent = parent;
    }

    public ArgsTreeBuilder addPossibleArgs(String ... args) {
        return addPossibleArgs(List.of(args));
    }

    public ArgsTreeBuilder addPossibleArgs(Collection<String> args) {
        args.forEach(this::addPossibleArg);

        return this;
    }

    public ArgsTreeBuilder addPossibleArg(String arg) {
        return addPossibleArg(new Candidate(arg));
    }

    public ArgsTreeBuilder addPossibleArg(Candidate candidate) {
        candidates.add(candidate);

        return this;
    }

    public ArgsTreeBuilder root() {
        ArgsTreeBuilder b = this;

        while (b.parent() != null)
            b = b.parent();

        return b;
    }

    public ArgsTreeBuilder addTip(Candidate candidate) {
        Completer completer = (reader, line, cands) -> cands.add(new Candidate(
                        candidate.value().isEmpty() ? line.word() : candidate.value(),
                        candidate.displ(),
                        candidate.group(),
                        candidate.descr(),
                        candidate.suffix(),
                        candidate.key(),
                        candidate.complete(),
                        candidate.sort()
                )
        );

        return addCompleter(completer);
    }

    public ArgsTreeBuilder addCompleter(Completer completer) {
        candidates.add(completer);

        return this;
    }

    public ArgsTreeBuilder addTip(String display, String description) {
        return addTip(new Candidate("", display, null, description, null, null, false));
    }

    public ArgsTreeBuilder setCompleter(Completer completer) {
        this.completer = completer;

        return this;
    }

    public ArgsTreeBuilder subTree() {
        ArgsTreeBuilder sub = new ArgsTreeBuilder(this);
        candidates.add(sub);

        return sub;
    }

    public ArgsRecursiveTreeBuilder recursiveSubTree() {
        ArgsRecursiveTreeBuilder sub = new ArgsRecursiveTreeBuilder(this);
        candidates.add(sub);

        return sub;
    }

    public ArgsTreeBuilder parent() {
        return parent;
    }

    public Completers.TreeCompleter.Node build() {
        return new Completers.TreeCompleter.Node(getCandidatesAsCompleter(), getNodes());
    }

    public Completer getCandidatesAsCompleter() {
        List<Candidate> filteredCandidates = candidates.stream()
                .filter((c) -> c instanceof Candidate)
                .map((c) -> (Candidate) c)
                .toList();

        return completer != null ? completer : (r, l, c) -> c.addAll(filteredCandidates);
    }

    public ArrayList<Completers.TreeCompleter.Node> getNodes() {
        ArrayList<Completers.TreeCompleter.Node> nodes = new ArrayList<>();

        nodes.addAll(candidates.stream()
                .filter((c) -> c instanceof ArgsTreeBuilder)
                .map((c) -> (ArgsTreeBuilder) c)
                .map(ArgsTreeBuilder::build)
                .toList()
        );

        nodes.addAll(candidates.stream()
                .filter((c) -> c instanceof Completers.TreeCompleter.Node)
                .map((c) -> (Completers.TreeCompleter.Node) c)
                .toList()
        );

        nodes.addAll(candidates.stream()
                .filter((c) -> c instanceof Completer)
                .map((c) -> (Completer) c)
                .map(Completers.TreeCompleter::node)
                .toList()
        );

        return nodes;
    }

}
