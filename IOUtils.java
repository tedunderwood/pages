/**
 * 
 */
package hmm;

/**
 * @author tunderwood
 *
 */
public class IOUtils {
	
	public static double roundDigits(double toRound, int places) {
		double multiplier = Math.pow(10, places);
		toRound = Math.round(multiplier * toRound) / multiplier;
		return toRound;
	}
	
	public static void writeTSV(double[][] matrix, String file, int places) {
		LineWriter writer = new LineWriter(file, true);
		String[] outlines = new String[matrix.length];
		for (int i = 0; i < matrix.length; ++i) {
			double[] row = matrix[i];
			String newline = Double.toString(roundDigits(row[0], places));
			for (int j = 1; j < row.length; ++j) {
				newline = newline + "\t" + roundDigits(row[j], places);
			}
			outlines[i] = newline;
		}
		writer.send(outlines);
	}
	
	public static void main (String args[]) {
		double test = 0.123456789;
		System.out.println(roundDigits(test, 2));
		System.out.println(roundDigits(test, 4));
	}
}
