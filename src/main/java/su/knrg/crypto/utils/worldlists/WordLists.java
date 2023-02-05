package su.knrg.crypto.utils.worldlists;

import su.knrg.crypto.Main;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import static su.knrg.crypto.utils.worldlists.Resources.LISTS_PATH;

public class WordLists {
    protected static HashMap<String, WordList> lists = new HashMap<>();
    protected static WordList activeList;

    public static void preload() {
        if (activeList != null)
            return;

        setActiveList("bip39_english");
    }

    public static WordList setActiveList(String name) {
        if (!lists.containsKey(name) && !loadList(name))
            return null;

        activeList = lists.get(name);

        return activeList;
    }

    public static WordList getActiveList() {
        return activeList;
    }

    private static boolean loadList(String name){
        if (lists.containsKey(name))
            return true;

        String path = LISTS_PATH + name + ".list";
        String text = null;

        try {
            text = new String(Main.class.getResourceAsStream(path).readAllBytes());
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

    public static class WordList {
        protected String name;
        protected String[] array;
        protected Map<String, Integer> map;

        public WordList(String name, String[] array, Map<String, Integer> map) {
            this.name = name;
            this.array = array;
            this.map = map;
        }

        public String getName() {
            return name;
        }

        public Optional<String> getWord(int index) {
            return Optional.ofNullable(index >= 0 && index < array.length ? array[index] : null);
        }

        public Optional<Integer> getIndex(String word) {
            return Optional.ofNullable(map.get(word));
        }

        // TODO: fix encapsulation leak
        public String[] getArray() {
            return array;
        }

        public Map<String, Integer> getMap() {
            return map;
        }
    }
}
