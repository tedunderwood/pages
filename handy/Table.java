/**
 * 
 */
package handy;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

/**
 * @author tunder
 *
 */
public class Table {
	
	String filePath;
	String separator = "\t";
	boolean fileFound = false;
	int rows = 0;
	int columns = 0;
	String[] columnNames;
	Map<String, Integer> rowindex;
	Map<String, Integer> colindex;
	List<List<String>> rowsInCols;
	
	/**
	 * Constructs a Table while overriding default separator, <code>\t</code>.
	 * 
	 * @param filePath Path to file containing tabular data.
	 * @param separator Separator to be specified by user.
	 */
	public Table (String filePath, String separator) {
		
		this.separator = separator;
		this.filePath = filePath;
		LineReader reader = new LineReader(filePath);
		ArrayList<String> filelines = new ArrayList<String>();
		
		try {
			filelines = reader.readList();
			fileFound = true;
		} catch (InputFileException e) {
			fileFound = false;
		}
		
		if (fileFound) parseTable(filelines);
	}
	
	/**
	 * Constructs a Table using default separator, <code>\t</code>.
	 * 
	 * @param filePath The path to a file containing tabular data.
	 */
	public Table (String filePath) {
		
		this.filePath = filePath;
		LineReader reader = new LineReader(filePath);
		ArrayList<String> filelines = new ArrayList<String>();
		
		try {
			filelines = reader.readList();
			fileFound = true;
		} catch (InputFileException e) {
			fileFound = false;
		}
		
		if (fileFound) parseTable(filelines);
	}
	
	/**
	 * Parses a list of lines into a tabular structure that can be indexed by
	 * column or row. We assume that: 1) the table has a header in the first row naming columns,
	 * and 2) there are no newline characters within each line, and 3) the values in the first column
	 * of the table are unique row indexes. No great harm will be done if assumption (3) is false;
	 * you just won't be able to use the row-index.
	 * 
	 * @param lines
	 * @return 	A boolean value indicating whether the table parsed successfully. May not currently
	 * 			be used for anything.
	 */
	private boolean parseTable(ArrayList<String> lines) {
		
		boolean success = true;
		int linecount = lines.size();
		rows = linecount - 1;
		// minus one because line zero is the header
		if (rows < 1) return false;
		
		String header = lines.get(0);
		header = header.replace("\n", "");
		String[] columnNames = header.split(separator);
		columns = columnNames.length;
		colindex = new HashMap<String, Integer>();
		for (int i = 0; i < columns; ++ i) {
			colindex.put(columnNames[i], i);
		}
		
		rowsInCols = new ArrayList<List<String>>(columns);
		for (int i = 0; i < columns; ++i) {
			List<String> newColumn = new ArrayList<String>(rows);
			rowsInCols.add(newColumn);
		}
		
		for (int i = 1; i < linecount; ++ i) {
			String thisline = lines.get(i);
			thisline = thisline.replace("\n", "");
			String[] fields = thisline.split(separator);
			if (fields.length != columns) {
				System.out.println("Field count of " + fields.length + " not equal to expected column count of "
						+ columns + " in " + filePath);
				success = false;
			}
			for (int j = 0; j < fields.length; ++j) {
				rowsInCols.get(j).add(fields[j]);
			}
		}
		
		rowindex = new HashMap<String, Integer>(rows); 
		for (int i = 0; i < rows; ++i) {
			rowindex.put(rowsInCols.get(0).get(i), i);
		}
		
		return success;
	}
	
	public String getCell(String aColumn, String aRow) {
		if (!colindex.containsKey(aColumn)) return "";
		if (!rowindex.containsKey(aRow)) return "";
		
		int row = rowindex.get(aRow);
		int col = colindex.get(aColumn);
		
		return rowsInCols.get(col).get(row);
	}
	
	
}
