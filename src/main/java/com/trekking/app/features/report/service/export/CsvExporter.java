package com.trekking.app.features.report.service.export;

import java.util.List;
import org.springframework.stereotype.Component;

/** Shared CSV builder for report exports; cells are quoted when needed. */
@Component
public class CsvExporter {
    public String toCsv(List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, header);
        rows.forEach(row -> appendRow(csv, row));
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) csv.append(',');
            csv.append(escape(cells.get(i)));
        }
        csv.append('\n');
    }

    private static String escape(String cell) {
        if (cell == null) return "";
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
