package me.dablakbandit.annotateconfig;

public enum NamingStrategy {
    EXACT {
        @Override
        public String translate(String input) {
            return input;
        }
    },
    LOWER_KEBAB_CASE {
        @Override
        public String translate(String input) {
            return splitWords(input, "-");
        }
    },
    LOWER_SNAKE_CASE {
        @Override
        public String translate(String input) {
            return splitWords(input, "_");
        }
    },
    LOWER_DOT_CASE {
        @Override
        public String translate(String input) {
            return splitWords(input, ".");
        }
    };

    public abstract String translate(String input);

    private static String splitWords(String input, String separator) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String normalized = input
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim();
        return normalized.replaceAll("\\s+", " ")
            .toLowerCase()
            .replace(" ", separator);
    }
}
