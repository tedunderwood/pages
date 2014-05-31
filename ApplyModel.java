/**
 * 
 */
package pages;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author tunderwood
 * 
 */
public class ApplyModel {

	/**
	 * @param args
	 */
	static String ridge = "3";
	static int featureCount;
	static Corpus corpus;
	static int numGenres;
	static int numInstances;
	static ArrayList<String> genres;
	static final String[][] EQUIVALENT = { { "bio", "non", "adver", "aut"}, {"bookp", "front"}, {"libra", "back", "index"}};
	static final double MARKOVSMOOTHING = .0001d;
	static Vocabulary vocabulary;

	public static void main(String[] args) {
		String featureDir = "/Users/tunder/Dropbox/pagedata/pagefeatures/";
		String genreDir = "/Users/tunder/Dropbox/pagedata/genremaps/";
		String dirToProcess = "/Users/tunder/Dropbox/pagedata/pagefeatures/";
		String dirForOutput = "/Users/tunder/output/genremaps/";
		String vocabPath = "/Users/tunder/Dropbox/pagedata/vocabulary.txt";
		
		Vocabulary vocabulary = new Vocabulary(vocabPath, 1000, true);
		// reads in the first 1000 features and adds a catch-all category
		// if there are fewer than 1000 features in vocab, it reads them all
		
		ArrayList<String> filesToProcess = DirectoryList.getStrippedPGTSVs(dirToProcess);
		
		featureCount = vocabulary.vocabularySize;
		System.out.println(featureCount + " features.");
		corpus = new Corpus(featureDir, genreDir, vocabulary);
		numGenres = corpus.genres.getSize();
		System.out.println(numGenres);
		numInstances = corpus.numPoints;
		genres = corpus.genres.genreLabels;
		vocabulary = Corpus.vocabulary;
		ArrayList<String> features = Corpus.features;
		
		ArrayList<WekaDriver> classifiers = new ArrayList<WekaDriver>(numGenres);

		for (int i = 0; i < numGenres; ++i) {
			String aGenre = "";
			if (i >= 2){
				aGenre = genres.get(i);
			}
			else {
				aGenre = genres.get(2);
			}
			// The first two genres are dummy positions for the front and back of the volume
			// so I can't train classifiers for them. Instead we just train dummy classifiers
			// for the first real genre, #2.
			WekaDriver classifyGenre = new WekaDriver(corpus.genres,
					features, aGenre, corpus.datapoints, ridge, true);
			classifiers.add(classifyGenre);
		}
		
		
		MarkovTable markov = corpus.makeMarkovTable(corpus.volumeLabels, MARKOVSMOOTHING);
		
		for (String thisFile : filesToProcess) {
			System.out.println(thisFile);
			ArrayList<String> wrapper = new ArrayList<String>();
			wrapper.add(thisFile);
			
			Corpus thisVolume = new Corpus(dirToProcess, wrapper);
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
//			double[] randomvalues = smoothedProbs.get(50);
//			for (double value : randomvalues) {
//				System.out.println("prob " + String.valueOf(value));
//			}
			ArrayList<String> rawPredictions = interpretEvidence(rawProbs);
			ArrayList<String> predictions = interpretEvidence(smoothedProbs);
			
			String outFile = thisFile + ".predict";
			String outPath = dirForOutput + "/" + outFile;
			
			LineWriter writer = new LineWriter(outPath, false);

			String[] outlines = new String[numPoints];
			for (int i = 0; i < numPoints; ++i) {
				outlines[i] = thesePages.get(i).label + "\t" + rawPredictions.get(i) + "\t" + predictions.get(i);
			}
			writer.send(outlines);
		}
	}
		

	private static ArrayList<String> interpretEvidence(ArrayList<double[]> probs) {
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

	public static boolean genresAreEqual(String predictedGenre,
			String targetGenre) {
		if (predictedGenre.equals(targetGenre)) {
			return true;
		} else {
			for (String[] row : EQUIVALENT) {
				if (Arrays.asList(row).contains(predictedGenre) & Arrays.asList(row).contains(targetGenre))
					return true;
			}
		}
		return false;
	}

}
