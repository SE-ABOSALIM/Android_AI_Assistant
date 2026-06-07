package com.example.anroidaiassistant.resources;

public final class AppSourceAliases {
    private AppSourceAliases() {}

    public static final SourceRule[] SOURCE_RULES = {
            new SourceRule("Google", new String[]{"com.google."}, new String[]{".google."}),
            new SourceRule("Microsoft", new String[]{"com.microsoft.", "com.azure."}, new String[]{".microsoft.", ".azure."}),
            new SourceRule("Meta", new String[]{"com.facebook.", "com.instagram.", "com.whatsapp."}, new String[]{}),
            new SourceRule("Telegram", new String[]{"org.telegram."}, new String[]{".telegram."}),
            new SourceRule("Spotify", new String[]{"com.spotify."}, new String[]{}),
            new SourceRule("Netflix", new String[]{"com.netflix."}, new String[]{}),
            new SourceRule("Turk Telekom", new String[]{}, new String[]{"turktelekom", "turk.telekom"}),
            new SourceRule("System", new String[]{"com.android."}, new String[]{})
    };

    public static final class SourceRule {
        public final String source;
        public final String[] startsWith;
        public final String[] contains;

        private SourceRule(String source, String[] startsWith, String[] contains) {
            this.source = source;
            this.startsWith = startsWith;
            this.contains = contains;
        }
    }
}
