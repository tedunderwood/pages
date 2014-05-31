/**
 * 
 */
package pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author tunderwood
 * 
 */
public class ParallelModel {

	/**
	 * This was originally based on Genre/pages/ApplyModel. Changed it by parallelizing
	 * both the training of the model and the application of the model.
	 * 
	 * @param args
	 */
	static int NTHREADS = 10;
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
		
		ExecutorService executive = Executors.newFixedThreadPool(NTHREADS);
		ArrayList<TrainingThread> trainingThreads = new ArrayList<TrainingThread>(numGenres);
		
		for (int i = 0; i < numGenres; ++i) {
			String aGenre;
			if (i < 2) aGenre = "dummy";
			else aGenre = genres.get(i);
			// The first two genres are dummy genres for the front and back of the volume. So we don't actually train classifiers
			// for them. The trainingThread class knows to return a dummy classifier when aGenre.equals("dummy").
			
			TrainingThread trainClassifier = new TrainingThread(corpus.genres, features, aGenre, corpus.datapoints, ridge, true);
			trainingThreads.add(trainClassifier);
		}
		
		for (int i = 0; i < numGenres; ++i) {
			executive.execute(trainingThreads.get(i));
		}
		
		executive.shutdown();
		// stops the addition of new threads; pool will terminate when these threads have completed
		try {
			executive.awaitTermination(6000, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			System.out.println("Helpful error message: Execution was interrupted.");
		}
		// block until all threads are completed
		
		ArrayList<WekaDriver> classifiers = new ArrayList<WekaDriver>(numGenres);
		
		for (int i = 0; i < numGenres; ++ i) {
			classifiers.add(trainingThreads.get(i).classifier);
		}
			
		MarkovTable markov = corpus.makeMarkovTable(corpus.volumeLabels, MARKOVSMOOTHING);
		
		ExecutorService classifierPool = Executors.newFixedThreadPool(NTHREADS);
		ArrayList<ClassifyingThread> filesToClassify = new ArrayList<ClassifyingThread>(filesToProcess.size());
		
		for (String thisFile : filesToProcess) {
			ClassifyingThread fileClassifier = new ClassifyingThread(thisFile, dirToProcess, dirForOutput, numGenres, 
					classifiers, markov, genres);
			filesToClassify.add(fileClassifier);
		}
		
		for (ClassifyingThread fileClassifier: filesToClassify) {
			classifierPool.execute(fileClassifier);
		}
		
		classifierPool.shutdown();
		try {
			executive.awaitTermination(6000, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			System.out.println("Helpful error message: Execution was interrupted.");
		}
		// block until all threads are completed
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
