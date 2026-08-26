package com.example.anroidaiassistant.ui.screens;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import androidx.core.content.ContextCompat;

import com.example.anroidaiassistant.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PrivacyPolicyFormatter {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BULLET = Pattern.compile("^-\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\.\\s+(.+)$");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)]\\([^)]+\\)");

    private PrivacyPolicyFormatter() {}

    static CharSequence format(Context context, String source) {
        List<Block> blocks = parse(source);
        SpannableStringBuilder output = new SpannableStringBuilder();
        List<ParagraphRange> paragraphRanges = new ArrayList<>();
        BlockKind previousKind = null;
        boolean documentTitleSkipped = false;
        int secondaryText = ContextCompat.getColor(context, R.color.app_text_secondary);
        int accent = ContextCompat.getColor(context, R.color.app_primary_dark);
        int codeBackground = ContextCompat.getColor(context, R.color.app_surface_muted);

        for (Block block : blocks) {
            if (!documentTitleSkipped
                    && block.kind == BlockKind.HEADING
                    && block.headingLevel == 1) {
                documentTitleSkipped = true;
                continue;
            }

            appendBlockSpacing(output, previousKind, block.kind);
            int start = output.length();

            if (block.kind == BlockKind.BULLET) {
                int markerStart = output.length();
                output.append("•  ");
                output.setSpan(
                        new ForegroundColorSpan(accent),
                        markerStart,
                        output.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                appendInline(output, block.text, accent, codeBackground);
            } else if (block.kind == BlockKind.NUMBERED) {
                int markerStart = output.length();
                output.append(block.number).append(".  ");
                output.setSpan(
                        new ForegroundColorSpan(accent),
                        markerStart,
                        output.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                output.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        markerStart,
                        output.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                appendInline(output, block.text, accent, codeBackground);
            } else {
                appendInline(output, block.text, accent, codeBackground);
            }

            int end = output.length();
            if (block.kind == BlockKind.HEADING) {
                applyHeadingStyle(
                        output,
                        start,
                        end,
                        block.headingLevel,
                        accent,
                        secondaryText
                );
            } else if (block.kind == BlockKind.BULLET) {
                paragraphRanges.add(new ParagraphRange(start, end, dp(context, 20)));
            } else if (block.kind == BlockKind.NUMBERED) {
                paragraphRanges.add(new ParagraphRange(start, end, dp(context, 26)));
            }
            previousKind = block.kind;
        }

        for (ParagraphRange range : paragraphRanges) {
            int paragraphEnd = range.end;
            if (paragraphEnd < output.length() && output.charAt(paragraphEnd) == '\n') {
                paragraphEnd++;
            }
            output.setSpan(
                    new LeadingMarginSpan.Standard(0, range.restLineIndent),
                    range.start,
                    paragraphEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return output;
    }

    static String plainText(String source, boolean omitDocumentTitle) {
        StringBuilder output = new StringBuilder();
        BlockKind previousKind = null;
        boolean documentTitleSkipped = false;
        for (Block block : parse(source)) {
            if (omitDocumentTitle
                    && !documentTitleSkipped
                    && block.kind == BlockKind.HEADING
                    && block.headingLevel == 1) {
                documentTitleSkipped = true;
                continue;
            }
            appendBlockSpacing(output, previousKind, block.kind);
            if (block.kind == BlockKind.BULLET) {
                output.append("•  ");
            } else if (block.kind == BlockKind.NUMBERED) {
                output.append(block.number).append(".  ");
            }
            output.append(stripInlineMarkdown(block.text));
            previousKind = block.kind;
        }
        return output.toString().trim();
    }

    private static List<Block> parse(String source) {
        List<Block> blocks = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) {
            return blocks;
        }

        PendingBlock pending = null;
        for (String rawLine : source.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                pending = flush(blocks, pending);
                continue;
            }

            Matcher heading = HEADING.matcher(trimmed);
            Matcher bullet = BULLET.matcher(trimmed);
            Matcher numbered = NUMBERED.matcher(trimmed);
            if (heading.matches()) {
                pending = flush(blocks, pending);
                blocks.add(Block.heading(heading.group(1).length(), heading.group(2)));
            } else if (bullet.matches()) {
                pending = flush(blocks, pending);
                pending = PendingBlock.bullet(bullet.group(1));
            } else if (numbered.matches()) {
                pending = flush(blocks, pending);
                pending = PendingBlock.numbered(numbered.group(1), numbered.group(2));
            } else if (pending != null
                    && pending.kind != BlockKind.PARAGRAPH
                    && startsWithWhitespace(rawLine)) {
                pending.append(trimmed);
            } else {
                if (pending == null) {
                    pending = PendingBlock.paragraph(trimmed);
                } else if (pending.kind == BlockKind.PARAGRAPH) {
                    pending.append(trimmed);
                } else {
                    pending = flush(blocks, pending);
                    pending = PendingBlock.paragraph(trimmed);
                }
            }
        }
        flush(blocks, pending);
        return blocks;
    }

    private static PendingBlock flush(List<Block> blocks, PendingBlock pending) {
        if (pending != null) {
            blocks.add(pending.toBlock());
        }
        return null;
    }

    private static boolean startsWithWhitespace(String value) {
        return !value.isEmpty() && Character.isWhitespace(value.charAt(0));
    }

    private static void appendBlockSpacing(
            SpannableStringBuilder output,
            BlockKind previous,
            BlockKind current
    ) {
        if (output.length() == 0) {
            return;
        }
        output.append(spacing(previous, current));
    }

    private static void appendBlockSpacing(
            StringBuilder output,
            BlockKind previous,
            BlockKind current
    ) {
        if (output.length() == 0) {
            return;
        }
        output.append(spacing(previous, current));
    }

    private static String spacing(BlockKind previous, BlockKind current) {
        if (current == BlockKind.HEADING) {
            return "\n\n";
        }
        if (previous == BlockKind.HEADING) {
            return "\n";
        }
        if ((previous == BlockKind.BULLET && current == BlockKind.BULLET)
                || (previous == BlockKind.NUMBERED && current == BlockKind.NUMBERED)) {
            return "\n";
        }
        return "\n\n";
    }

    private static void applyHeadingStyle(
            SpannableStringBuilder output,
            int start,
            int end,
            int level,
            int accent,
            int secondaryText
    ) {
        int sizeSp;
        if (level <= 1) {
            sizeSp = 22;
        } else if (level == 2) {
            sizeSp = 19;
        } else if (level == 3) {
            sizeSp = 17;
            output.setSpan(
                    new ForegroundColorSpan(accent),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        } else {
            sizeSp = 16;
            output.setSpan(
                    new ForegroundColorSpan(secondaryText),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        output.setSpan(
                new AbsoluteSizeSpan(sizeSp, true),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        output.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    private static void appendInline(
            SpannableStringBuilder output,
            String markdown,
            int accent,
            int codeBackground
    ) {
        int index = 0;
        while (index < markdown.length()) {
            if (markdown.startsWith("**", index)) {
                int closing = markdown.indexOf("**", index + 2);
                if (closing > index + 2) {
                    int start = output.length();
                    output.append(markdown.substring(index + 2, closing));
                    output.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            start,
                            output.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = closing + 2;
                    continue;
                }
            }
            if (markdown.charAt(index) == '`') {
                int closing = markdown.indexOf('`', index + 1);
                if (closing > index + 1) {
                    int start = output.length();
                    output.append(markdown.substring(index + 1, closing));
                    int end = output.length();
                    output.setSpan(
                            new TypefaceSpan("monospace"),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    output.setSpan(
                            new BackgroundColorSpan(codeBackground),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    output.setSpan(
                            new ForegroundColorSpan(accent),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = closing + 1;
                    continue;
                }
            }
            if (markdown.charAt(index) == '[') {
                int labelEnd = markdown.indexOf("](", index + 1);
                int urlEnd = labelEnd < 0 ? -1 : markdown.indexOf(')', labelEnd + 2);
                if (labelEnd > index + 1 && urlEnd > labelEnd + 2) {
                    int start = output.length();
                    output.append(markdown.substring(index + 1, labelEnd));
                    int end = output.length();
                    output.setSpan(
                            new URLSpan(markdown.substring(labelEnd + 2, urlEnd)),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    output.setSpan(
                            new ForegroundColorSpan(accent),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = urlEnd + 1;
                    continue;
                }
            }
            output.append(markdown.charAt(index));
            index++;
        }
    }

    private static String stripInlineMarkdown(String markdown) {
        Matcher links = MARKDOWN_LINK.matcher(markdown);
        return links.replaceAll("$1")
                .replace("**", "")
                .replace("`", "");
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private enum BlockKind {
        HEADING,
        PARAGRAPH,
        BULLET,
        NUMBERED
    }

    private static final class Block {
        private final BlockKind kind;
        private final String text;
        private final int headingLevel;
        private final String number;

        private Block(BlockKind kind, String text, int headingLevel, String number) {
            this.kind = kind;
            this.text = text;
            this.headingLevel = headingLevel;
            this.number = number;
        }

        private static Block heading(int level, String text) {
            return new Block(BlockKind.HEADING, text, level, "");
        }
    }

    private static final class PendingBlock {
        private final BlockKind kind;
        private final String number;
        private final StringBuilder text;

        private PendingBlock(BlockKind kind, String number, String text) {
            this.kind = kind;
            this.number = number;
            this.text = new StringBuilder(text);
        }

        private static PendingBlock paragraph(String text) {
            return new PendingBlock(BlockKind.PARAGRAPH, "", text);
        }

        private static PendingBlock bullet(String text) {
            return new PendingBlock(BlockKind.BULLET, "", text);
        }

        private static PendingBlock numbered(String number, String text) {
            return new PendingBlock(BlockKind.NUMBERED, number, text);
        }

        private void append(String continuation) {
            text.append(' ').append(continuation);
        }

        private Block toBlock() {
            return new Block(kind, text.toString(), 0, number);
        }
    }

    private static final class ParagraphRange {
        private final int start;
        private final int end;
        private final int restLineIndent;

        private ParagraphRange(int start, int end, int restLineIndent) {
            this.start = start;
            this.end = end;
            this.restLineIndent = restLineIndent;
        }
    }
}
