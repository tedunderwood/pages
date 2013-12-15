package hmm;

import java.util.ArrayList;

public class TestFwdBwd {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ArrayList<double[]> evidenceVector = new ArrayList<double[]>(5);
		double[] a = {0.6, 0.5, 0.4};
		evidenceVector.add(a);
		double[] b = {0.7, 0.3, 0.3};
		evidenceVector.add(b);
		double[] c = {0.2, 0.2, 0.8};
		evidenceVector.add(c);
		double[] d = {0.3, 0.5, 0.5};
		evidenceVector.add(d);
		double[] e = {0.1, 0.1, 0.9};
		evidenceVector.add(e);
		
		double[][] matrix = {{0.8, 0.1, 0.1}, {0.2, 0.6, 0.2}, {0.2, 0.1, 0.7}};
		MarkovTable markov = new MarkovTable(matrix);
		
		ArrayList<double[]> results = ForwardBackward.smooth(evidenceVector, markov);
		for (double[] row: results) {
			System.out.println(row[0] + "\t" + row[1] + "\t" + row[2]);
		}
	}

}
