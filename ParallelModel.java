/**
 * 
 */
package pages;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author tunderwood
 * 
 * This was originally based on Genre/pages/ApplyModel.java, which was ultimately based on
 * HMM/hmm/ApplyModel.java (written 2013).
 * 
 * In late May, 2014, I changed it by parallelizing both the training of the model 
 * and the application of the model (classification of unknown volumes). 
 * Then (June 2, 2014) refactored to allow for crossvalidation.
 * 
 * @param NFOLDS       Number of folds to create if crossvalidating. E.g., tenfold.
 * @param NTHREADS     Number of threads to parallelize across; the same number is used
 *                     for parallelizing training and classification.
 * @param ridge        The ridge parameter for regularizing logistic regression.
 * @param featureCount The number of features in the model. This will be greater than
 *                     the number of words in the vocabulary, because it also includes
 *                     structural features.
 *          
 */
public class ParallelModel {

	static int NTHREADS = 10;
	static int NFOLDS = 5;
	static String ridge = "50";
	static int featureCount;
	static int numGenres;
	static int numInstances;
	static ArrayList<String> genres;
	static final String[][] EQUIVALENT = { { "bio", "non", "adver", "aut"}, {"bookp", "front"}, {"libra", "back", "index"}};
	static final double MARKOVSMOOTHING = .0001d;
	static Vocabulary vocabulary;

	public static void main(String[] args) {
		String sourceKind = "mixedtraining";
		String featureDir = "/Users/tunder/Dropbox/pagedata/" + sourceKind + "/pagefeatures/";
		String genreDir = "/Users/tunder/Dropbox/pagedata/" + sourceKind + "/genremaps/";
		String dirToProcess = "/Users/tunder/Dropbox/pagedata/" + sourceKind + "/pagefeatures/";
		String vocabPath = "/Users/tunder/Dropbox/pagedata/mixedvocabulary.txt";
		String dirForOutput;
		boolean crossvalidate = true;
		
		if (crossvalidate) {
			dirForOutput = "/Users/tunder/output/mixedtraining/";
		}
		else {
			dirForOutput = "/Users/tunder/output/genremaps/";
		}
		
		vocabulary = new Vocabulary(vocabPath, 1000, true);
		// reads in the first 1000 features and adds a catch-all category
		// if there are fewer than 1000 features in vocab, it reads them all
		
		File featureFolder = new File(featureDir);
		File[] featureFiles = featureFolder.listFiles();
		
		File genreFolder = new File(genreDir);
		File[] genreFiles = genreFolder.listFiles();
		
		System.out.println(genreFiles.length);
		
		ArrayList<String> volumeLabels = folderIntersection(featureFiles, genreFiles);
		int numVolumes = volumeLabels.size();
		System.out.println("Intersection of " + numVolumes);

		ArrayList<String> filesToProcess = DirectoryList.getStrippedPGTSVs(dirToProcess);
		
		if (crossvalidate) {
			// Our approach to crossvalidation is simply to divide the volumes into N sublists (folds) and run
			// N train-and-classify sequences. In each case we train a model on all volumes *not* in
			// fold N, and then test that model only on volumes in fold N.
			
			// This approach is conceptually simple but it can break in the unlikely event that you
			// get a training set where a particular genre category isn't represented. Since "genres" are
			// currently implemented as a field of the TrainingCorpus they get generated anew each time you
			// create a new TrainingCorpus.
			
			boolean firstPass = true;
			GenreList oldList = new GenreList();
			// if the code executes properly this list will actually never be used; it will be
			// replaced on the first pass.
			
			Partition partition = new Partition(filesToProcess, NFOLDS);
			for (int i = 0; i < NFOLDS; ++i) {
				System.out.println("Iteration: " + Integer.toString(i));
				ArrayList<String> trainingSet = partition.volumesExcluding(i);
				ArrayList<String> testSet = partition.volumesInFold(i);
				GenreList newGenreList = trainAndClassify(trainingSet, featureDir, genreDir, 
						dirToProcess, testSet, dirForOutput);
				
				if (firstPass) {
					oldList = newGenreList;
					firstPass = false;
				}
				else {
					if (!oldList.equals(newGenreList)) {
						// note that we override the definition of equals for GenreLists.
						System.out.println("Genre lists vary between folds of the corpus.");
					}
				}
			}
		}
		else {
			trainAndClassify(volumeLabels, featureDir, genreDir, dirToProcess, filesToProcess, dirForOutput);
		}
	
		System.out.println("DONE.");
	}
	
	/**
	 * This is the workhorse method for this whole package. It trains a model on a given set
	 * of volumes and applies it to another set of volumes. Those sets can be identical, or
	 * can be disjunct (e.g. in crossvalidation).
	 * 
	 * @param trainingVols     A set of volumes IDs to be used in training. These are basically the part
	 *                         of the filename that precedes the extension.
	 * @param featureDir       Directory for page-level feature counts: we assume extension is ".pg.tsv"
	 * @param genreDir         Directory for genre maps: we assume extension is ".map"
	 * @param inputDir         Source directory for files to be classified.
	 * @param volsToProcess    A list of files in that directory to be classified. Here and above, note
	 *                         that we do not assume all files in the directory will be classified.
	 * @param dirForOutput     Self-explanatory. This is where the .map files that result from classification
	 *                         will be written out.
	 */
	private static GenreList trainAndClassify(ArrayList<String> trainingVols, String featureDir, String genreDir, 
			String inputDir, ArrayList<String> volsToProcess, String dirForOutput) {
		
		featureCount = vocabulary.vocabularySize;
		System.out.println(featureCount + " features.");
		TrainingCorpus corpus = new TrainingCorpus(featureDir, genreDir, trainingVols, vocabulary);
		numGenres = corpus.genres.getSize();
		System.out.println(numGenres);
		numInstances = corpus.numPoints;
		genres = corpus.genres.genreLabels;
		ArrayList<String> features = TrainingCorpus.features;
		
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
			
		MarkovTable markov = corpus.makeMarkovTable(trainingVols, MARKOVSMOOTHING);
		
		ExecutorService classifierPool = Executors.newFixedThreadPool(NTHREADS);
		ArrayList<ClassifyingThread> filesToClassify = new ArrayList<ClassifyingThread>(volsToProcess.size());
		
		for (String thisFile : volsToProcess) {
			ClassifyingThread fileClassifier = new ClassifyingThread(thisFile, inputDir, dirForOutput, numGenres, 
					classifiers, markov, genres);
			filesToClassify.add(fileClassifier);
		}
		
		for (ClassifyingThread fileClassifier: filesToClassify) {
			classifierPool.execute(fileClassifier);
		}
		
		classifierPool.shutdown();
		try {
			classifierPool.awaitTermination(6000, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			System.out.println("Helpful error message: Execution was interrupted.");
		}
		// block until all threads are completed
		
		
		// write prediction metadata (confidence levels)
		
		String outPath = dirForOutput + "/predictionMetadata.tsv";
		
		LineWriter metadataWriter = new LineWriter(outPath, true);
		String[] metadata = new String[filesToClassify.size()];
		int i = 0;
		for (ClassifyingThread completedClassification : filesToClassify) {
			metadata[i] = completedClassification.predictionMetadata;
			i += 1;
		}
		metadataWriter.send(metadata);
		
		return corpus.genres;
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
	
	/**
	 * Given lists of files in two different folders, with different extensions,
	 * creates a list of the intersection between them -- i.e., a list of parts before
	 * the extension that are found in both folders.
	 * 
	 * In particular, it assumes that the featureFiles will have a ".pg.tsv" extension
	 * and the genre files will have a ".map" extension.
	 *  
	 * @param genreFiles
	 * @param featureFiles
	 * @return
	 */
	private static ArrayList<String> folderIntersection(File[] featureFiles, File[] genreFiles) {
		
		ArrayList<String> hathiIDs = new ArrayList<String>();
		
		for (File aFile: featureFiles) {
			if (!aFile.isFile()) continue;
			// because we don't want directories, etc.
			String filename = aFile.getName();
			int namelength = filename.length();
			if (namelength < 8) continue;
			else {
				int sevenback = namelength - 7;
				// We assume that each file in this folder should end with ".pg.tsv"
				String suffix = filename.substring(sevenback, namelength);
				if (!suffix.equals(".pg.tsv")) continue;
				String idPart = filename.substring(0, sevenback);
				
				boolean isMatched = false;
				
				for (File genreFile: genreFiles) {
					if (!genreFile.isFile()) continue;
					// because we don't want directories, etc.
					String matchname = genreFile.getName();
					int matchlength = matchname.length();
					if (matchlength < 5) continue;
					else {
						int fourback = matchlength - 4;
						// We assume that each file in this folder should end with ".map"
						String anothersuffix = matchname.substring(fourback, matchlength);
						if (!anothersuffix.equals(".map")) continue;
						String anotherIdPart = matchname.substring(0, fourback);
						if (idPart.equals(anotherIdPart)) {
							isMatched = true;
							break;
						}
					}
				}
				
				if (isMatched) hathiIDs.add(idPart);
			}
		}
		return hathiIDs;
	}

}
