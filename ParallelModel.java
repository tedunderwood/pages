/**
 * 
 */
package pages;

import java.io.*;
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
	static String ridge = "100";
	static int featureCount;
	static int numGenres;
	static int numInstances;
	static ArrayList<String> genres;
	static final String[][] EQUIVALENT = { { "bio", "non", "adver", "aut"}, {"bookp", "front"}, {"libra", "back", "index"}};
	static final double MARKOVSMOOTHING = .0001d;
	static Vocabulary vocabulary;

	public static void main(String[] args) {
		
		// training rootdir = "/Users/tunder/Dropbox/pagedata/"
		// 
		
		ArgumentParser parser = new ArgumentParser(args);
		boolean trainingRun = parser.isPresent("-train");
		
		String dirToProcess;
		String dirForOutput;
		String vocabPath = "/Users/tunder/Dropbox/pagedata/mixedvocabulary.txt";
		
		if (parser.isPresent("-output")) {
			dirForOutput = parser.getString("-output");
		}
		else {
			dirForOutput = "/Users/tunder/output/" + parser.getString("-tbranch") + "/";
		}
		
		if (trainingRun) {
			String trainingRootDir = parser.getString("-troot");
			String trainingBranch = parser.getString("-tbranch");
			String featureDir = trainingRootDir + trainingBranch + "/pagefeatures/";
			String genreDir = trainingRootDir + trainingBranch + "/genremaps/";
			if (parser.isPresent("-self")) dirToProcess = featureDir;
			else dirToProcess = parser.getString("-toprocess");
			boolean crossvalidate = parser.isPresent("-cross");
			boolean serialize = parser.isPresent("-save");
			if (crossvalidate) serialize = false;
			
			trainingRun (vocabPath, featureDir, genreDir, dirToProcess, dirForOutput, crossvalidate, serialize);
		}
		else {
			String modelPath = parser.getString("-model");
		}
		
	}
	
	private static void trainingRun (String vocabPath, String featureDir, String genreDir, 
			String dirToProcess, String dirForOutput, boolean crossvalidate, boolean serialize) {
		
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
		
		String outPath = dirForOutput + "/predictionMetadata.tsv";
		LineWriter metadataWriter = new LineWriter(outPath, false);
		metadataWriter.print("htid\tmaxprob\tgap");
		// Create header for predictionMetadata file, overwriting any
		// previous file.
		
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
						dirToProcess, testSet, dirForOutput, serialize);
				
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
			trainAndClassify(volumeLabels, featureDir, genreDir, dirToProcess, filesToProcess, dirForOutput, serialize);
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
			String inputDir, ArrayList<String> volsToProcess, String dirForOutput, boolean serialize) {
		
		Model model = trainModel(trainingVols, featureDir, genreDir);
		
		MarkovTable markov = model.markov;
		ArrayList<String> genres = model.genreList.genreLabels;
		FeatureNormalizer normalizer = model.normalizer;
		ArrayList<WekaDriver> classifiers = model.classifiers;
		int numGenres = genres.size();
		
		
		ExecutorService classifierPool = Executors.newFixedThreadPool(NTHREADS);
		ArrayList<ClassifyingThread> filesToClassify = new ArrayList<ClassifyingThread>(volsToProcess.size());
		
		for (String thisFile : volsToProcess) {
			ClassifyingThread fileClassifier = new ClassifyingThread(thisFile, inputDir, dirForOutput, numGenres, 
					classifiers, markov, genres, vocabulary, normalizer);
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
		
		if (serialize) {
			 try {
		         FileOutputStream fileOut =
		         new FileOutputStream(dirForOutput + "/Model.ser");
		         ObjectOutputStream out = new ObjectOutputStream(fileOut);
		         out.writeObject(model);
		         out.close();
		         fileOut.close();
		         System.out.printf("Serialized data is saved in " + dirForOutput + "/Model.ser");
		      }
			 catch(IOException except) {
		          except.printStackTrace();
		      }
		}
		return model.genreList;
	}
	
	private static Model trainModel(ArrayList<String> trainingVols, String featureDir, String genreDir) {
		
		featureCount = vocabulary.vocabularySize;
		System.out.println(featureCount + " features.");
		TrainingCorpus corpus = new TrainingCorpus(featureDir, genreDir, trainingVols, vocabulary);
		numGenres = corpus.genres.getSize();
		System.out.println(numGenres);
		numInstances = corpus.numPoints;
		genres = corpus.genres.genreLabels;
		FeatureNormalizer normalizer = corpus.normalizer;
		ArrayList<String> features = normalizer.features;
		
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
		
		Model model = new Model(vocabulary, normalizer, corpus.genres, classifiers, markov);
		return model;
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
