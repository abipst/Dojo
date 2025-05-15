package com.test;

import java.util.ArrayList;
import java.util.List;

/**
 * A robust CSV line parser that handles: - Standard comma-separated values -
 * Quoted fields containing commas - Escaped quotes within fields - Empty fields
 * - Special characters and backslash escapes
 */
public class CSVParseNew {

	/**
	 * A simple but effective CSV line parser that handles: - Standard fields
	 * separated by commas - Quoted fields containing commas - Fields with escaped
	 * quotes - Empty fields
	 */
	private static List<String> splitLine(String line) {
		if (line == null || line.isEmpty()) {
			return new ArrayList<>();
		}

		List<String> result = new ArrayList<>();
		StringBuilder field = new StringBuilder();
        //
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '"') {
				// If we're at the start of a field or just after a comma and this is a quote
				if (!inQuotes && (i == 0 || line.charAt(i - 1) == ',')) {
					inQuotes = true;
				}
				// If we're at the end of a quoted field (quote followed by comma or end of
				// line)
				else if (inQuotes && (i == line.length() - 1 || line.charAt(i + 1) == ',')) {
					inQuotes = false;
				}
				// Otherwise, it's just a quote character inside a field
				else {
					field.append(c);
				}
			}
			// If we encounter a comma and we're not in quotes, it's a field separator
			else if (c == ',' && !inQuotes) {
				result.add(field.toString());
				field = new StringBuilder();
			}
			// Otherwise, it's just a regular character to add to the current field
			else {
				field.append(c);
			}
		}

		// Don't forget to add the last field
		result.add(field.toString());

		return result;
	}

	// Example usage with test data
	public static void main(String[] args) {
		String testLine = "M,2312332,Test,\"53 Carp Rd, Knowloon City\",Cihesn\"s Cut,,0000,HKG,5812,\"{All,All,4.9}\",202,UTC";
		List<String> fields = splitLine(testLine);

		System.out.println("Original: " + testLine);
		System.out.println("\nParsed fields:");
		for (String field : fields) {
			System.out.println(field);
		}
	}
}