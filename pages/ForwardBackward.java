/**
 * The ForwardBackward algorithm for Hidden Markov Models.
 * 
 */
package pages;

import java.util.ArrayList;

/**
 * @author tunderwood
 *
 */
public class ForwardBackward {
	
	public static void print(double[] row) {
		System.out.println(row[0] + "\t" + row[1] + "\t" + row[2]);
	}

	public static ArrayList<double[]> smooth(ArrayList<double[]> evidenceVectors, MarkovTable markov) {
		 
		int time = evidenceVectors.size();
		int numGenres = evidenceVectors.get(0).length;
		
		// Forward.
		ArrayList<double[]> forward = new ArrayList<double[]>();
		// stores the normalized state probability for each step
		ArrayList<Double> probabilityOfTheEvidence = new ArrayList<Double>();
		
		int beginning = 0;
		// this is just the assigned genre number for the beginning of every sequence.
		double[] nextstep = markov.transitionProbs(beginning);
		
		
		assert(nextstep.length == numGenres);
		double sum = 0d;
		for (int j = 0; j < numGenres; ++j) {
			nextstep[j] = nextstep[j] * evidenceVectors.get(0)[j];
			sum += nextstep[j];
		}
		probabilityOfTheEvidence.add(sum);
		
		// normalize by this sum
		nextstep = normalize(nextstep, sum);
		forward.add(nextstep);
		
		for (int i = 1; i < time; ++i) {
			nextstep = new double[numGenres];
			sum = 0d;
			
			for (int j = 0; j < numGenres; ++j) {
				// j iterates over possible previous states
				double[] fromStateJ = markov.transitionProbs(j);
				double inStateJ = forward.get(i-1)[j];
				for (int k = 0; k < numGenres; ++k) {
					// k iterates over possible following states
					nextstep[k] +=  inStateJ * fromStateJ[k];
				}	
			}
			// Now nextstep contains the one-step-ahead predictive density.
		
			for (int j = 0; j < numGenres; ++j) {
				nextstep[j] = nextstep[j] * evidenceVectors.get(i)[j];
				sum += nextstep[j];
			}
			probabilityOfTheEvidence.add(sum);
			nextstep = normalize(nextstep, sum);
			
//			// error-checking
//			if (Double.isNaN(sum) | sum == 0) {
//				System.out.println("At step " + String.valueOf(i));
//				System.out.println("sum = " + String.valueOf(sum));
//			}
			
			
			forward.add(nextstep);
			
		}
		
		// Now backward!
		int ending = 1;
		// this is just the assigned genre number for the end of every sequence
		ArrayList<double[]> backward = new ArrayList<double[]>();
		for (int i = 0; i < time; ++ i) {
			backward.add(new double[numGenres]);
		}
		
		assert(probabilityOfTheEvidence.size() == time);
		
		sum = 0d;
		for (int i = 0; i < numGenres; ++i) {
			nextstep[i] = markov.transitionProbs(i)[ending] * evidenceVectors.get(time-1)[i];
			// that's the chance that i would produce an ending times the evidence for i on the page itself.
			sum += nextstep[i];
		}
		nextstep = normalize(nextstep, sum);
		backward.set(time - 1, nextstep);
		
		for (int i = (time - 2); i > -1; --i) {
			nextstep = new double[numGenres];
			sum = 0d;
			
			for (int j = 0; j < numGenres; ++j) {
				// j iterates over possible previous states
				double[] fromStateJ = markov.transitionProbs(j);
				for (int k = 0; k < numGenres; ++k) {
					// k iterates over possible following states
					double inStateK = backward.get(i+1)[k];
					nextstep[j] += inStateK * fromStateJ[k];
				}
				nextstep[j] = nextstep[j] * evidenceVectors.get(i)[j];
				sum += nextstep[j];
			}
			// Now nextstep contains the one-step-ahead predictive density.
			
			nextstep = normalize(nextstep, sum);
			backward.set(i, nextstep);
		}
		
// No longer needed; was used to debug.	
//		writeComponents("/Users/tunderwood/uniquefiction/", forward, backward, evidenceVectors);
		
		// Now to combine.
		ArrayList<double[]> forwardbackward = new ArrayList<double[]>();
		for (int i = 0; i < time; ++ i) {
			sum = 0d;
			nextstep = new double[numGenres];
			for (int j = 0; j < numGenres; ++ j) {
				nextstep[j] = forward.get(i)[j] * backward.get(i)[j];
				sum += nextstep[j];
			}
			nextstep = normalize(nextstep, sum);
			forwardbackward.add(nextstep);
		}
		
		return forwardbackward;
	}
	
	public static double[] normalize(double[] input, double sum) {
		for (int i = 0; i < input.length; ++ i) {
			input[i] = input[i] / sum;
		}	
		return input;
	}
	
	public static void writeComponents (String directory, ArrayList<double[]> forward, ArrayList<double[]> backward, ArrayList<double[]> evidence) {
		String forwardpath = directory + "forward.tsv";
		LineWriter writer = new LineWriter(forwardpath, false); // don't append
		String[] outlines = new String[forward.size()];
		
		int i = 0;
		for (double[] row : forward) {
			String line = "";
			for (double value : row) {
				line = line + String.valueOf(value) + "\t";
			}
		outlines[i] = line + "\n";
		++ i;
		}
		
		writer.send(outlines);
		
		String backwardpath = directory + "backward.tsv";
		writer = new LineWriter(backwardpath, false); // don't append
		outlines = new String[backward.size()];
		
		i = 0;
		for (double[] row : backward) {
			String line = "";
			for (double value : row) {
				line = line + String.valueOf(value) + "\t";
			}
		outlines[i] = line + "\n";
		++ i;
		}
		
		writer.send(outlines);
		
		String evidencepath = directory + "evidence.tsv";
		writer = new LineWriter(evidencepath, false); // don't append
		outlines = new String[evidence.size()];
		
		i = 0;
		for (double[] row : evidence) {
			String line = "";
			for (double value : row) {
				line = line + String.valueOf(value) + "\t";
			}
		outlines[i] = line + "\n";
		++ i;
		}
		
		writer.send(outlines);
	}
}
