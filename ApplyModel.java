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
		String wordcountFile = "/Users/tunderwood/Dropbox/PythonScripts/mine/pagepartdata/pagecounts.csv";
		String genreFile = "/Users/tunderwood/Dropbox/PythonScripts/mine/pagepartdata/pagemap.tsv";
		String dirToProcess = "/Users/tunderwood/uniquefiction/counts7/";
		String dirForOutput = "/Users/tunderwood/uniquefiction/maps7/";
		ArrayList<String> filesToProcess = DirectoryList.getCSVs(dirToProcess);
		
		featureCount = 225;
		corpus = new Corpus(wordcountFile, genreFile, featureCount);
		numGenres = corpus.genres.getSize();
		numInstances = corpus.numPoints;
		genres = corpus.genres.genreLabels;
		Vocabulary vocabulary = corpus.vocabulary;
		
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
					Corpus.features, aGenre, corpus.datapoints, ridge, true);
			classifiers.add(classifyGenre);
		}
		
		
		MarkovTable markov = corpus.makeMarkovTable(corpus.volumeLabels, MARKOVSMOOTHING);
		
		for (String thisFile : filesToProcess) {
			String nextPath = dirToProcess + thisFile;
			System.out.println(thisFile);
			
			Corpus thisVolume = new Corpus(nextPath, featureCount, vocabulary);
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
			
			int fileNameLen = thisFile.length();
			String outFile = thisFile.substring(0, fileNameLen - 4) + ".tsv";
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
