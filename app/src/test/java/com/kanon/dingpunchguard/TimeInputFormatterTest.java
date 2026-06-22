package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimeInputFormatterTest {
    @Test
    public void formatsFourDigitTimeWithLeadingZero() {
        assertEquals("08:30", TimeInputFormatter.format("0830"));
    }

    @Test
    public void keepsLeadingZeroThreeDigitInputUnformattedWhileTyping() {
        assertEquals("083", TimeInputFormatter.format("083"));
    }

    @Test
    public void formatsThreeDigitTimeWithoutLeadingZero() {
        assertEquals("8:30", TimeInputFormatter.format("830"));
    }

    @Test
    public void stripsExistingSeparatorBeforeFormatting() {
        assertEquals("08:30", TimeInputFormatter.format("08:30"));
    }

    @Test
    public void movesCursorToEndAfterFormattingFourDigits() {
        TimeInputFormatter.Result result = TimeInputFormatter.format("0830", 4, 4);

        assertEquals("08:30", result.text);
        assertEquals(5, result.selectionStart);
        assertEquals(5, result.selectionEnd);
    }

    @Test
    public void mapsBackspaceFromFormattedEndToEditableDigits() {
        TimeInputFormatter.Result result = TimeInputFormatter.format("08:3", 4, 4);

        assertEquals("083", result.text);
        assertEquals(3, result.selectionStart);
        assertEquals(3, result.selectionEnd);
    }

    @Test
    public void preservesCursorAfterExistingSeparator() {
        TimeInputFormatter.Result result = TimeInputFormatter.format("08:30", 3, 3);

        assertEquals("08:30", result.text);
        assertEquals(3, result.selectionStart);
        assertEquals(3, result.selectionEnd);
    }
}
