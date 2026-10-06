package com.hrportal.storage;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;

/** Content-Disposition headers for stored files: a plain filename, plus an encoded one only when needed. */
public final class Dispositions {

    private Dispositions() {
    }

    public static String inline(String fileName) {
        return of(ContentDisposition.inline(), fileName);
    }

    public static String attachment(String fileName) {
        return of(ContentDisposition.attachment(), fileName);
    }

    private static String of(ContentDisposition.Builder builder, String fileName) {
        boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(fileName);
        return (ascii ? builder.filename(fileName) : builder.filename(fileName, StandardCharsets.UTF_8)).build().toString();
    }
}
