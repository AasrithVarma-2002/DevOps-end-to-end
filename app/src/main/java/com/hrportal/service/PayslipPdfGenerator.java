package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.domain.Payslip;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Renders a payslip as a one-page A4 PDF (OpenPDF). Amounts are in INR. */
@Component
public class PayslipPdfGenerator {

    private static final Color BRAND = new Color(0x1f, 0x4e, 0x79);
    private static final Color LINE = new Color(0xdf, 0xe4, 0xea);
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND);
    private static final Font LABEL = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
    private static final Font VALUE = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font HEAD = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);

    public byte[] generate(Payslip p) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph("HR Portal Pro", TITLE));
        doc.add(new Paragraph("Payslip for " + PayrollService.label(p.getMonth()), BOLD));
        doc.add(spacer());

        Employee e = p.getEmployee();
        PdfPTable who = table(4);
        pair(who, "Employee", e.getFullName());
        pair(who, "Employee code", e.getEmployeeCode());
        pair(who, "Job title", e.getJobTitle());
        pair(who, "Department", e.getDepartment().getName());
        pair(who, "Days in month", String.valueOf(p.getDaysInMonth()));
        pair(who, "Paid days", days(p.getPaidDays()));
        pair(who, "Loss of pay days", days(p.getLopDays()));
        pair(who, "Monthly salary", money(p.getMonthlySalary()));
        doc.add(who);
        doc.add(spacer());

        PdfPTable pay = table(4);
        header(pay, "Earnings", "Amount (INR)", "Deductions", "Amount (INR)");
        line(pay, "Basic", p.getBasic(), "Provident fund (12% of basic)", p.getProvidentFund());
        line(pay, "House rent allowance", p.getHra(), "Professional tax", p.getProfessionalTax());
        line(pay, "Special allowance", p.getSpecialAllowance(), "", null);
        total(pay, "Gross earnings", p.getGrossEarned(), "Total deductions", p.getTotalDeductions());
        doc.add(pay);
        doc.add(spacer());

        doc.add(new Paragraph("Net pay: INR " + money(p.getNetPay()), TITLE));
        doc.add(spacer());
        doc.add(new Paragraph("Income tax (TDS) is not included. This is a computer-generated payslip and needs no signature.", LABEL));
        doc.close();
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ layout helpers

    private static PdfPTable table(int columns) {
        PdfPTable t = new PdfPTable(columns);
        t.setWidthPercentage(100);
        return t;
    }

    private static void pair(PdfPTable t, String label, String value) {
        t.addCell(cell(new Phrase(label, LABEL), Element.ALIGN_LEFT, null));
        t.addCell(cell(new Phrase(value, VALUE), Element.ALIGN_LEFT, null));
    }

    private static void header(PdfPTable t, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            t.addCell(cell(new Phrase(titles[i], HEAD), i % 2 == 1 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, BRAND));
        }
    }

    private static void line(PdfPTable t, String earning, BigDecimal earned, String deduction, BigDecimal deducted) {
        t.addCell(cell(new Phrase(earning, VALUE), Element.ALIGN_LEFT, null));
        t.addCell(cell(new Phrase(money(earned), VALUE), Element.ALIGN_RIGHT, null));
        t.addCell(cell(new Phrase(deduction, VALUE), Element.ALIGN_LEFT, null));
        t.addCell(cell(new Phrase(deducted == null ? "" : money(deducted), VALUE), Element.ALIGN_RIGHT, null));
    }

    private static void total(PdfPTable t, String earning, BigDecimal earned, String deduction, BigDecimal deducted) {
        t.addCell(cell(new Phrase(earning, BOLD), Element.ALIGN_LEFT, null));
        t.addCell(cell(new Phrase(money(earned), BOLD), Element.ALIGN_RIGHT, null));
        t.addCell(cell(new Phrase(deduction, BOLD), Element.ALIGN_LEFT, null));
        t.addCell(cell(new Phrase(money(deducted), BOLD), Element.ALIGN_RIGHT, null));
    }

    private static PdfPCell cell(Phrase phrase, int align, Color background) {
        PdfPCell c = new PdfPCell(phrase);
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        c.setBorderColor(LINE);
        if (background != null) {
            c.setBackgroundColor(background);
        }
        return c;
    }

    private static Paragraph spacer() {
        return new Paragraph(" ");
    }

    /** 123456 → "123,456.00". */
    private static String money(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(value);
    }

    private static String days(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
