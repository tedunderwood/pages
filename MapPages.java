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
 * @param minutesToWait	How long to wait for the ExecutorService governing classification
 * 						to terminate.
 * @param RIDGE        The ridge parameter for regularizing logistic regression.
 * @param featureCount The number of features in the model. This will be greater than
 *                     the number of words in the vocabulary, because it also includes
 *                     structural features.
 *          
 */
public class MapPages {

	static int NTHREADS = 10;
	static int NFOLDS = 5;
	static int minutesToWait = 30;
	static String RIDGE = "60";
	static int featureCount;
	static int numGenres;
	static int numInstances;
	static ArrayList<String> genres;
	static final String[][] EQUIVALENT = { { "bio", "non", "adver", "aut"}, {"bookp", "front"}, {"libra", "back", "index"}};
	static final double MARKOVSMOOTHING = .0001d;
	static Vocabulary vocabulary;

	/**
	 * Main method: mostly argument-parsing.
	 * 
	 * @param args 	Options set at the command line.
	 * -train			Include if a model is to be trained; otherwise we expect
	 * -model (path)	Path to a previously-trained model.
	 * -output (dir)	Directory for all output.
	 * -troot (dir)		Directory for training data; needs to include subdirectories
	 * 					/pagefeatures and /genremaps.
	 * -tbranch (subdir)	If specified, defines a subdirectory of -troot for training data.
	 * -toprocess (dir)	Directory of files to be classified. Not needed if you specify
	 * -self			Which implies that training/pagefeatures will be classified.
	 * -cross (int)		Number of crossvalidation folds; e.g., five-fold. The int parameter
	 * 					is optional. Default 5.
	 * -save			Model will be saved to output directory. We don't save multiple cross-
	 * 					validation models, so incompatible with -cross.
	 * -local			Indicates that the model will be applied to a local directory. Otherwise we expect
	 * -pairtreeroot (dir)	The root of a pairtree hierarchy, and
	 * -slice (path)		Path to a file containing dirty HathiTrust ids that imply pairtree paths to vols.
	 * -nthreads (int)	Number of threads to run in parallel. Default 10.
	 * -ridge (double)	Ridge parameter for regularizing logistic regression.		
	 */
	public static void main(String[] args) {
		WarningLogger.initializeLogger(true, "/home/tunder/java/genre/warninglog.txt");
		
		// We send command-line arguments to a parser and then query the parser
		// to find whether certain options are present, and what values are assigned
		// to them.
		
		ArgumentParser parser = new ArgumentParser(args);
		boolean trainingRun = parser.isPresent("-train");
		// The most important option defines whether this is a training run.
		
		String dirToProcess;
		String dirForOutput;
		String featureDir;
		String genreDir;
		String vocabPath = "/Users/tunder/Dropbox/pagedata/mixedvocabulary.txt";
		
		if (parser.isPresent("-output")) {
			dirForOutput = parser.getString("-output");
		}
		else {
			dirForOutput = "/Volumes/TARDIS/output/" + parser.getString("-tbranch") + "/";
		}
		
		dirForOutput = validateDirectory(dirForOutput, "output");
		
		if (parser.isPresent("-nthreads")) {
			NTHREADS = parser.getInteger("-nthreads");
		}
		
		if (parser.isPresent("-ridge")) {
			RIDGE = parser.getString("-ridge");
		}
		
		if (trainingRun) {
			String trainingRootDir = parser.getString("-troot");
			trainingRootDir = validateDirectory(trainingRootDir, "training root");
			
			if (parser.isPresent("-tbranch")) {
				String trainingBranch = parser.getString("-tbranch");
				if (trainingBranch.startsWith("/") | trainingBranch.startsWith("/")) {
					System.out.println("The -tbranch parameter should not include slashes.");
					trainingBranch = trainingBranch.replace("/",  "");
				}
				
				featureDir = trainingRootDir + trainingBranch + "/pagefeatures/";
				genreDir = trainingRootDir + trainingBranch + "/genremaps/";
			}
			else {
				featureDir = trainingRootDir + "pagefeatures/";
				genreDir = trainingRootDir + "genremaps/";
			}
			
			if (parser.isPresent("-self")) dirToProcess = featureDir;
			else dirToProcess = parser.getString("-toprocess");
			dirToProcess = validateDirectory(dirToProcess, "input");
			
			boolean crossvalidate = parser.isPresent("-cross");
			if (crossvalidate) {
				if (parser.getInteger("-cross") > 0) {
					NFOLDS = parser.getInteger("-cross");
				}
			}
			boolean serialize = parser.isPresent("-save");
			if (crossvalidate) serialize = false;
			
			trainingRun (vocabPath, featureDir, genreDir, dirToProcess, dirForOutput, crossvalidate, serialize);
		}
		else {
			boolean local = parser.isPresent("-local");
			if (local) {
				dirToProcess = parser.getString("-toprocess");
				ArrayList<String> volsToProcess = DirectoryList.getStrippedPGTSVs(dirToProcess);
				String modelPath = parser.getString("-model");
				Model model = deserializeModel(modelPath);
				applyModel(model, dirToProcess, volsToProcess, dirForOutput, false);
				// The final argument == false because this is not a pairtree process.
			}
			else {
				// We infer that this model is going to be applied to volumes in a pairtree structure.
				
				String slicePath = parser.getString("-slice");
				// The path to a list of dirty HTIDs specifying volume locations.
				ArrayList<String> dirtyHtids = getSlice(slicePath);
				dirToProcess = parser.getString("-pairtreeroot");
				
				minutesToWait = 500;
				// If this is being run on a pairtree, it's probably quite a large workset.
				
				String modelPath = parser.getString("-model");
				Model model = deserializeModel(modelPath);
				
				applyModel(model, dirToProcess, dirtyHtids, dirForOutput, true);
				// The final argument == true because this is a pairtree process.
			}
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
				// The important outputs of trainAndClassify are obviously, the genre 
				// predictions that get written to file inside the methof. But the method also 
				// returns a GenreList, which allows us to check that all genres are 
				// represented in each pass of crossvalidation.
				
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
	 * This is a workhorse method for this package. It trains a model on a given set
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
	private static GenreList trainAndClassify (ArrayList<String> trainingVols, String featureDir, String genreDir, 
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
					classifiers, markov, genres, vocabulary, normalizer, false);
			// The final parameter == false because this will never be run in a pairtree context.
			
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
		         System.out.printf("Serialized data is saved in " + dirForOutput + "/Model.ser\n");
		      }
			 catch(IOException except) {
		          except.printStackTrace();
		      }
		}
		return model.genreList;
	}
	
	private static Model trainModel (ArrayList<String> trainingVols, String featureDir, String genreDir) {
		
		featureCount = vocabulary.vocabularySize;
		System.out.println(featureCount + " features.");
		Corpus corpus = new Corpus(featureDir, genreDir, trainingVols, vocabulary);
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
			
			TrainingThread trainClassifier = new TrainingThread(corpus.genres, features, aGenre, corpus.datapoints, RIDGE, true);
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
	
	private static Model deserializeModel (String modelPath) {
		Model m = null;
	    try {
	    	FileInputStream fileIn = new FileInputStream(modelPath);
	        ObjectInputStream in = new ObjectInputStream(fileIn);
	        m = (Model) in.readObject();
	        in.close();
	        fileIn.close();
	      }
	    catch(IOException except) {
	         except.printStackTrace();
	         return m;
	      }
	    catch(ClassNotFoundException c) {
	         System.out.println("Employee class not found");
	         c.printStackTrace();
	         return m;
	      }
	   return m;
	}

	/**
	 * Takes a previously-trained model and applies it to a new set of volumes.
	 * 
	 * @param model A wrapper for a set of classes that define the model.
	 * @param inputDir This can either be a directory that contains files, or the
	 * root directory of a pairtree structure.
	 * @param volsToProcess This is a list of file IDs. If this is being run on a local
	 * directory, these will be 'clean' volume IDs that can be used as filenames. If this is
	 * run on a pairtree, these will be 'dirty' volume IDs specifying a path to each file.
	 * @param dirForOutput Where to write results.
	 * @param isPairtree Boolean flag to tell us whether this is a pairtree run. It gets passed to
	 * the ClassifyingThread, which can invoke two different Corpus constructors depending on the
	 * underlying data source being used.
	 */
	private static void applyModel (Model model, String inputDir, ArrayList<String> volsToProcess, 
			String dirForOutput, boolean isPairtree) {
		
		vocabulary = model.vocabulary;
		MarkovTable markov = model.markov;
		ArrayList<String> genres = model.genreList.genreLabels;
		FeatureNormalizer normalizer = model.normalizer;
		ArrayList<WekaDriver> classifiers = model.classifiers;
		int numGenres = genres.size();
		
		System.out.println("Model loaded. Proceeding to apply it to unknown volumes.");
		
		ExecutorService classifierPool = Executors.newFixedThreadPool(NTHREADS);
		ArrayList<ClassifyingThread> filesToClassify = new ArrayList<ClassifyingThread>(volsToProcess.size());
		
		for (String thisFile : volsToProcess) {
			thisFile = PairtreeReader.cleanID(thisFile);
			// because they may have been passed in as dirty HathiTrust IDs with slashes and colons
			ClassifyingThread fileClassifier = new ClassifyingThread(thisFile, inputDir, dirForOutput, numGenres, 
					classifiers, markov, genres, vocabulary, normalizer, isPairtree);
			filesToClassify.add(fileClassifier);
		}
		
		for (ClassifyingThread fileClassifier: filesToClassify) {
			classifierPool.execute(fileClassifier);
		}
		
		classifierPool.shutdown();
		try {
			classifierPool.awaitTermination(minutesToWait, TimeUnit.MINUTES);
		}
		catch (InterruptedException e) {
			System.out.println("Helpful error message: Execution was interrupted.");
		}
		// block until all threads are completed
		
		System.out.println("Classification complete. Now writing metadata (confidence levels.)");
		
		// write prediction metadata (confidence levels)
		
		String outPath = dirForOutput + "/predictionMetadata.tsv";
		LineWriter headerWriter = new LineWriter(outPath, false);
		headerWriter.print("htid\tmaxprob\tgap");
		
		LineWriter metadataWriter = new LineWriter(outPath, true);
		
		String[] metadata = new String[filesToClassify.size()];
		int i = 0;
		for (ClassifyingThread completedClassification : filesToClassify) {
			metadata[i] = completedClassification.predictionMetadata;
			i += 1;
		}
		metadataWriter.send(metadata);
	}
	
	public static boolean genresAreEqual (String predictedGenre,
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

	private static ArrayList<String> getSlice(String slicePath) {
		ArrayList<String> dirtyHtids;
		LineReader getHtids = new LineReader(slicePath);
		try {
			dirtyHtids = getHtids.readList();
		}
		catch (InputFileException e) {
			System.out.println("Missing slice file: " + slicePath);
			dirtyHtids = null;
		}
		return dirtyHtids;
	}
	
	/**
	 * We expect directories to exist, and we expect them to end with a slash "/."
	 * This method ensures both things are true.
	 * 
	 * @param dir Directory to validate.
	 * @param description To use in error message.
	 * @return A directory that ends with a slash.
	 */
	private static String validateDirectory(String dir, String description) {
		File outputCheck = new File(dir);
		if (!outputCheck.isDirectory()) {
			System.out.println("This run is going to fail, because the " + description + " directory doesn't exist.");
			System.exit(0);
		}
		if (!dir.endsWith("/")) dir = dir + "/";
		return dir;
	}
}
