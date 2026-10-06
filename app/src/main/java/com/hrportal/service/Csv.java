package com.hrportal.service;

import java.math.BigDecimal;
import java.util.List;

/** Builds RFC 4180 CSV that opens cleanly in Excel. */
public final class Csv {

    /** Excel uses this to detect UTF-8 (names with accents, ₹). */
    public static final String BOM = "﻿";

    private final StringBuilder out = new StringBuilder(BOM);

    public Csv row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    public Csv rows(List<Object[]> rows) {
        rows.forEach(this::row);
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal d) {
            return d.toPlainString();
        }
        String s = value.toString();
        if (value instanceof CharSequence && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s; // a spreadsheet would otherwise run "=..." as a formula (CSV injection)
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
