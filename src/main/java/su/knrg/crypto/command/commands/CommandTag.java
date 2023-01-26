package su.knrg.crypto.command.commands;

public enum CommandTag {
    MISC("Misc"),
    BACKUPS("Backups"),
    CRYPTOGRAPHY("Cryptography"),
    CRYPTOCURRENCIES("Cryptocurrencies");

    public final String title;

    CommandTag(String title) {
        this.title = title;
    }
}
