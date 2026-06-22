package com.kanon.dingpunchguard;

final class TimeInputFormatter {
    private TimeInputFormatter() {
    }

    static final class Result {
        final String text;
        final int selectionStart;
        final int selectionEnd;

        Result(String text, int selectionStart, int selectionEnd) {
            this.text = text;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }
    }

    static String format(String value) {
        return format(value, value == null ? 0 : value.length(), value == null ? 0 : value.length()).text;
    }

    static Result format(String value, int selectionStart, int selectionEnd) {
        String source = value == null ? "" : value;
        String digits = digitsOnly(source);
        if (digits.length() > 4) {
            digits = digits.substring(0, 4);
        }
        String formatted = formatDigits(digits);
        int start = mapSelection(source, formatted, selectionStart);
        int end = mapSelection(source, formatted, selectionEnd);
        return new Result(formatted, start, end);
    }

    private static String digitsOnly(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                result.append(c);
                if (result.length() == 4) {
                    break;
                }
            }
        }
        return result.toString();
    }

    private static String formatDigits(String digits) {
        if (digits.length() <= 2) {
            return digits;
        }
        if (digits.length() == 3) {
            if (digits.charAt(0) == '0') {
                return digits;
            }
            return digits.substring(0, 1) + ":" + digits.substring(1);
        }
        return digits.substring(0, 2) + ":" + digits.substring(2);
    }

    private static int mapSelection(String source, String formatted, int selection) {
        int clamped = Math.max(0, Math.min(selection, source.length()));
        int digitIndex = 0;
        for (int i = 0; i < clamped; i++) {
            if (Character.isDigit(source.charAt(i))) {
                digitIndex++;
                if (digitIndex == 4) {
                    break;
                }
            }
        }
        boolean afterSeparator = clamped > 0 && !Character.isDigit(source.charAt(clamped - 1));
        return offsetForDigitIndex(formatted, digitIndex, afterSeparator);
    }

    private static int offsetForDigitIndex(String formatted, int digitIndex, boolean afterSeparator) {
        if (digitIndex <= 0) {
            return 0;
        }
        int digitsSeen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (Character.isDigit(formatted.charAt(i))) {
                digitsSeen++;
                if (digitsSeen == digitIndex) {
                    int offset = i + 1;
                    if (afterSeparator && offset < formatted.length() && formatted.charAt(offset) == ':') {
                        return offset + 1;
                    }
                    return offset;
                }
            }
        }
        return formatted.length();
    }
}
