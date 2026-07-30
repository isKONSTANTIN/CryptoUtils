package su.knst.crypto.tests.core.seed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.utils.worldlists.WordLists;

import static org.junit.jupiter.api.Assertions.*;

class WordListsTest {

    @BeforeAll
    static void setUp() {
        WordLists.preload();
    }

    @Test
    void listsTheAvailableWordlists() {
        assertTrue(WordLists.getLists().contains("bip39_english"));
        assertTrue(WordLists.getLists().contains("bip39_japanese"));
    }

    @Test
    void switchesTheActiveWordlistAndBack() {
        assertEquals("bip39_french", WordLists.setActiveList("bip39_french").name());
        assertEquals("bip39_english", WordLists.setActiveList("bip39_english").name());
    }

    @Test
    void anUnknownListNameYieldsNothing() {
        assertNull(WordLists.setActiveList("not_a_real_list"));
    }

    @AfterAll
    static void afterAll() {
        // WordLists' active list is process-global state shared with every other test class,
        // so make sure we leave it as the default the rest of the suite expects.
        WordLists.setActiveList("bip39_english");
    }
}
