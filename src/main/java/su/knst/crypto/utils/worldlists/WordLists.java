package su.knst.crypto.utils.worldlists;


import java.util.*;

public class WordLists {
    public static final String DEFAULT_LIST = "bip39_english";

    protected static final HashMap<String, WordList> lists = new HashMap<>();
    protected static WordList activeList;

    /** Loads the default list up front so the first seed operation doesn't pay for it. */
    public static void preload() {
        getActiveList();
    }

    public static WordList setActiveList(String name) {
        if (!lists.containsKey(name) && !loadList(name))
            return null;

        activeList = lists.get(name);

        return activeList;
    }

    /**
     * Falls back to the default list rather than returning null: callers convert seed phrases, and
     * having to remember to prime this first would make every one of them a step away from a
     * NullPointerException.
     */
    public static WordList getActiveList() {
        if (activeList == null)
            setActiveList(DEFAULT_LIST);

        return activeList;
    }

    private static boolean loadList(String name){
        if (lists.containsKey(name))
            return true;

        String path = Resources.LISTS_PATH + name + ".list";
        String text;

        try {
            text = new String(Objects.requireNonNull(WordLists.class.getResourceAsStream(path)).readAllBytes());
        } catch (Exception e) {
            return false;
        }

        String[] words = text.split("\n");
        HashMap<String, Integer> map = new HashMap<>();

        for (int i = 0; i < words.length; i++)
            map.put(words[i], i);

        lists.put(name, new WordList(name, words, map));

        return true;
    }

    public static List<String> getLists() {
        return Arrays.stream(Resources.values())
                .map(Enum::name)
                .map(String::toLowerCase)
                .toList();
    }

    public record WordList(String name, String[] array, Map<String, Integer> map) {
        public Optional<String> getWord(int index) {
            return Optional.ofNullable(index >= 0 && index < array.length ? array[index] : null);
        }

        public Optional<Integer> getIndex(String word) {
            return Optional.ofNullable(map.get(word));
        }
    }
}
