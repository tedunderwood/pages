package pages;

import java.util.ArrayList;
import java.util.Arrays;

public class ClassifyingThread implements Runnable {
	
	private String thisFile;
	private String inputDir;
	private String outputDir;
	private int numGenres;
	private ArrayList<String> genres;
	private ArrayList<WekaDriver> classifiers;
	private MarkovTable markov;
	
	public ClassifyingThread(String thisFile, String inputDir, String outputDir, int numGenres, 
			ArrayList<WekaDriver> classifiers, MarkovTable markov, ArrayList<String> genres) {
		this.thisFile = thisFile;
		this.inputDir = inputDir;
		this.outputDir = outputDir;
		this.numGenres = numGenres;
		this.classifiers = classifiers;
		this.markov = markov;
		this.genres = genres;
	}

	@Override
	public void run() {
		ArrayList<String> wrapper = new ArrayList<String>();
		wrapper.add(thisFile);
		
		TrainingCorpus thisVolume = new TrainingCorpus(inputDir, wrapper);
		int numPoints = thisVolume.numPoints;
		ArrayList<DataPoint> thesePages = thisVolume.datapoints;
	
		ArrayList<double[]> rawProbs = new ArrayList<double[]>(numPoints);
		for (int i = 0; i < numPoints; ++i) {
			double[] probs = new double[numGenres];
			Arrays.fill(probs, 0);
			rawProbs.add(probs);
		}
		
		for (int i = 2; i < numGenres; ++i) {
			WekaDriver classify = classifiers.get(i);
			double[][] probs = classify.testNewInstances(thesePages);
			for (int j = 0; j < numPoints; ++j) {
				rawProbs.get(j)[i] = probs[j][0];
			}
		}
		
		ArrayList<double[]> smoothedProbs = ForwardBackward.smooth(rawProbs, markov);
//		
//		double[] randomvalues = smoothedProbs.get(50);
//		for (double value : randomvalues) {
//			System.out.println("prob " + String.valueOf(value));
//		}
		ArrayList<String> rawPredictions = interpretEvidence(rawProbs);
		ArrayList<String> predictions = interpretEvidence(smoothedProbs);
		
		String outFile = thisFile + ".predict";
		String outPath = outputDir + "/" + outFile;
		
		LineWriter writer = new LineWriter(outPath, false);

		String[] outlines = new String[numPoints];
		for (int i = 0; i < numPoints; ++i) {
			outlines[i] = thesePages.get(i).label + "\t" + rawPredictions.get(i) + "\t" + predictions.get(i);
		}
		writer.send(outlines);
	}
	

	private ArrayList<String> interpretEvidence(ArrayList<double[]> probs) {
		int arraySize = probs.size();
		ArrayList<String> predictions = new ArrayList<String>(arraySize);
		for (int i = 0; i < arraySize; ++i) {
			predictions.add("none");
		}
	
		for (int i = 0; i < arraySize; ++i) {
			double maxprob = 0d;
			for (int j = 0; j < numGenres; ++j) {
				if (probs.get(i)[j] > maxprob) {
					maxprob = probs.get(i)[j];
					predictions.set(i, genres.get(j));
				}
			}
		}
		return predictions;
	}
}

