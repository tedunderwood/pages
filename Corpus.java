package pages;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.File;

public class Corpus {

	ArrayList<DataPoint> datapoints;
	// Individual pages, represented as vectors of features, plus labels.
	ArrayList<ArrayList<String>> volumeGenres;
	ArrayList<Volume> volumes;
	ArrayList<String> volumeLabels;
	int numPoints;
	int numVolumes;
	GenreList genres;
	// Primarily a list of genre labels. It is guaranteed to begin with two dummy genres,
	// "begin" and "end," which apply to the imaginary pages right before the start of the
	// volume and right after its end.
	static HashMap<String, Integer> featureMap;
	static int featureCount;
	static ArrayList<String> features;
	// Note that this list of features is going to be larger than the size of the vocabulary,
	// because it will include the STRUCTURALFEATURES contained in Global.
	static ArrayList<Double> stdevOfFeatures;
	static ArrayList<Double> meansOfFeatures;
	static Vocabulary vocabulary;

	public Corpus(String featurePath, String genrePath, Vocabulary vocab) {

		genres = new GenreList();
		vocabulary = vocab;
		featureMap = vocabulary.getMap();
		// In this version of corpus, we assume that we already have a preset vocabulary
		// for classification. Features is a map where the feature names are keys and
		// the values are integers representing sequence order.
		
		// Now we proceed to check the feature files and genre maps in our training corpus,
		// to make sure they align.
		
		File featureFolder = new File(featurePath);
		File[] featureFiles = featureFolder.listFiles();
		
		File genreFolder = new File(genrePath);
		File[] genreFiles = genreFolder.listFiles();
		
		System.out.println(genreFiles.length);
		
		volumeLabels = folderIntersection(featureFiles, genreFiles);
		numVolumes = volumeLabels.size();
		System.out.println("Intersection of " + numVolumes);
		
		// First read in a genre sequence for each volume.
		volumeGenres = getVolumeGenres(genrePath, volumeLabels);
		
		// Now read in a feature sequence for each volume
		
		volumes = readVolumes(featurePath, volumeLabels, featureMap);

		// The Volume objects read the lines associated with a single
		// HathiTrust volume ID. Then they can produce DataPoints that
		// either represent volumes or individual pages. I've implemented
		// these alternatives as two different methods of the Volume class.

		// In this Corpus constructor, we create datapoints for each page
		// that are then sorted and grouped by genre.

		numPoints = 0;
		datapoints = new ArrayList<DataPoint>();
		
		// Now we have three ArrayLists, all of which should have the same length and
		// contain data structures corresponding to each other in the same sequence.
		
		// volumeLabels is a list of volumeIDs, corresponding to
		// volumeGenres, a list containing an ArrayList of genres for the pages of each volume
		// and volumes, a list containing Volume data objects that can produce page-level
		// data points. Our task now is to match genres to page-level DataPoints, and
		// add the genre-tagged points to a master array "datapoints" containing all
		// the pages as individual objects.
		
		// We match pages purely by sequence. There are no absolute page numbers in the underlying
		// data structure, so there's no reason why we should ever have a "skipped" or "missing" page.
		// The 11th page is by definition between the 10th and the 12th.
		
		assert (volumeLabels.size() == volumes.size());
		assert (volumeGenres.size() == volumes.size());
		System.out.println("We have " + volumes.size() + " volumes in the training set.");
		
		// If either of those things are false, we're in big trouble.
		
		for (int i = 0; i < numVolumes; ++ i) {
			Volume thisVol = volumes.get(i);
			ArrayList<String> genresOfThisVol = volumeGenres.get(i);
			
			System.out.println(thisVol.volumeID);
			ArrayList<DataPoint> newPoints = thisVol.makePagePoints(featureMap);
			
			if (genresOfThisVol.size() != newPoints.size()) {
				System.out.println("Genre file for " + thisVol.volumeID + " has " + genresOfThisVol.size() +
						" pages, but the feature file has " + newPoints.size());
			}
			
			for (int j = 0; j < newPoints.size(); ++j) {
				String genre = genresOfThisVol.get(j);
				genre = normalizeGenre(genre);
				if (!genres.genreLabels.contains(genre)) {
					genres.addLabel(genre);
				}
				
				DataPoint aPoint = newPoints.get(j);
				aPoint.setGenre(genre);
				datapoints.add(aPoint);
				numPoints += 1;
			}
		}

		newNormalizeFeatures(vocabulary, true);
		// true because this is a training corpus
		
		ArrayWriter printMeans = new ArrayWriter("\t");
		printMeans.addStringColumn(features, "features");
		printMeans.addDoubleColumn(meansOfFeatures, "means");
		printMeans.writeToFile("/Users/tunder/output/featuremeans.tsv");
		
		System.out.println(genres.genreLabels);

	}
	
	private ArrayList<Volume> readVolumes(String featurePath, ArrayList<String> volumeLabels, HashMap<String, Integer> featureMap) {
		ArrayList<Volume> volumes = new ArrayList<Volume>();
		
		for (String volID : volumeLabels) {
			String volumePath = featurePath + volID + ".pg.tsv";
			LineReader fileSource = new LineReader(volumePath);
			String[] filelines = fileSource.readlines();
			Volume thisVol = new Volume(volID);
			
			for (String line : filelines) {
				String[] tokens = line.split("\t");
				int tokenCount = tokens.length;
				if (tokenCount != 3) {
					System.out.println("Token count not 3 at " + line);
					continue;
					// TODO: better error handling
				}
				// If the feature is either a) in the vocabulary or
				// b) a structural features, which always begins with a hashtag,
				// we pass it through unaltered.
				if (featureMap.containsKey(tokens[1]) | tokens[1].startsWith("#")) {
					thisVol.addFeature(tokens);
				}
				else {
					tokens[1] = "wordNotInVocab";
					// Words not in the vocabulary still need to be included, for instance,
					// in the total count of words per page. Also the density of rare words
					// is itself revealing. So we count these as a special feature,
					// "wordNotInVocab." Paradoxically, this is itself a word in the
					// vocabulary. :)
					thisVol.addFeature(tokens);
				}
			}
			// end iteration across lines
			volumes.add(thisVol);
		}
		// end iteration across volume labels
		 
		return volumes;
	}
	
	private ArrayList<ArrayList<String>> getVolumeGenres(String genrePath, ArrayList<String> volumeLabels) {
		volumeGenres = new ArrayList<ArrayList<String>>();
		for (String label: volumeLabels) {
			String genreFilePath = genrePath + label + ".map";
			LineReader fileSource = new LineReader(genreFilePath);
			String[] filelines = fileSource.readlines();
			
			ArrayList<String> aGenreSequence = new ArrayList<String>();
			int pagecounter = 0;
			
			for (String line : filelines) {
				String[] tokens = line.split("\t");
				int tokenCount = tokens.length;
				if (tokenCount != 2) {
					System.out.println("Error: tokenCount not equal to 2 at "
							+ line);
					// not the world's most sophisticated error handling here
					// TODO: define Exception handling for input format issues
					continue;
				}
				int pagenum = Integer.parseInt(tokens[0]);
				if (pagenum != pagecounter) {
					System.out.println("pagination oddity");
				}
				pagecounter += 1;
				String genre = tokens[1];
				genre = normalizeGenre(genre);
				if (!genres.genreLabels.contains(genre)) {
					genres.addLabel(genre);
				}
				aGenreSequence.add(genre);
			// end iterating across lines
			}
			volumeGenres.add(aGenreSequence);
		// end iterating across volumes
		}
	return volumeGenres;
	}
	
	/**
	 * A constructor that we use to create one-volume corpora when a training set
	 * has already been created and we're cycling through the test set. Note that
	 * it relies on static fields generated in the earlier construction of a 
	 * training corpus. The most explicit of these is the featureMap, but there
	 * are other static fields implicitly used inside the newNormalizeFeatures method.
	 * 
	 * @param featurePath Directory in which the source file is contained.
	 * @param volumeLabels An ArrayList of length one, containing the filename
	 * for the volume to be loaded in this corpus, minus ".pg.tsv", which will
	 * be added back in the readVolumes method.
	 *
	 */
	public Corpus(String featurePath, ArrayList<String> volumeLabels) {

		// In this builder of corpora, we assume a) that we only have
		// wordcounts and don't yet know the genres and b) a vocabulary
		// for the model has already been established.

		volumes = readVolumes(featurePath, volumeLabels, featureMap);

		// In this implementation of Corpus, we should actually
		// only have one volume in our data.
		
		if (volumes.size() > 1) {
			System.out.println("Error: multiple volumes in file.");
		}
		
		Volume thisVol = volumes.get(0);

		// We're producing page points.
		
		datapoints = thisVol.makePagePoints(featureMap);
		numPoints = datapoints.size();
		
//		System.out.println(thisVol.volumeID + "  " + String.valueOf(thisVol.maxPageNum) + "  " + String.valueOf(numPoints));
//		
//		Scanner keyboard = new Scanner(System.in);
//		String userInput = keyboard.nextLine();
		
		newNormalizeFeatures(vocabulary, false);
		// false because this is not a training corpus.
		
//		DataPoint aPoint = datapoints.get(50);
//		double[] randomvalues = aPoint.vector;
//		for (double value : randomvalues) {
//			System.out.println(value);
//		}

	}

	private static String normalizeGenre(String genre) {
		for (String[] row : Global.CONVERSIONS) {
			if (row[0].equals(genre)) {
				genre = row[1];
			}
		}
		return genre;
	}
	
	/**
	 * Centers all features on the feature mean, and normalizes them by their
	 * standard deviations. Aka, transforms features to z-scores. This is
	 * desirable because I'm using regularized logistic regression, which will
	 * shrink coefficients toward the origin, and shrinkage pressure is
	 * distributed more evenly if the features have been normalized.
	 * 
	 * This new version of the method avoids using the means computed in the vocabulary,
	 * which I do not trust or understand. It also has a flag that determines whether
	 * we're establishing means and stdevs (trainingCorpus) or using previously
	 * established normalization (testCorpus).
	 * 
	 */
	
	private void newNormalizeFeatures(Vocabulary vocabulary, boolean trainingCorpus) {
		String[] vocabularyArray = vocabulary.vocabularyArray;
		int vocabSize = vocabularyArray.length;
		
		int FEATURESADDED = Global.FEATURESADDED;
		
		if (trainingCorpus) {
			features = new ArrayList<String>(vocabSize + FEATURESADDED);
			meansOfFeatures = new ArrayList<Double>(vocabSize + FEATURESADDED);
			stdevOfFeatures = new ArrayList<Double>(vocabSize + FEATURESADDED);
			
			// Create the list of features.
			
			for (int i = 0; i < vocabSize; ++i) {
				features.add(vocabularyArray[i]);
			}
			for (String aFeature : Global.STRUCTURALFEATURES) {
				features.add(aFeature);
			}
			
			// Now get means.
			for (int i = 0; i < vocabSize + FEATURESADDED; ++i) {
				double sum = 0d;
				for (DataPoint aPoint : datapoints) {
					sum += aPoint.getVector()[i];
				}
				meansOfFeatures.add(sum / numPoints);
			}
	
			featureCount = vocabSize + FEATURESADDED;
			for (int i = 0; i < featureCount; ++i) {
				stdevOfFeatures.add(0d);
				for (DataPoint aPoint : datapoints) {
					double current = stdevOfFeatures.get(i);
					current = current
							+ Math.pow((aPoint.vector[i] - meansOfFeatures.get(i)),
									2);
					stdevOfFeatures.set(i, current);
				}
				// We've summed the variance; now divide by the number of points and
				// take sqrt to get stdev.
				stdevOfFeatures.set(i,
						Math.sqrt(stdevOfFeatures.get(i) / numPoints));
			}
		}
		// End the if statement that runs only when this is a training Corpus.

		double[] vector = new double[featureCount];
		// now normalize ALL the points!
		for (DataPoint aPoint : datapoints) {
			vector = aPoint.vector;
			for (int i = 0; i < featureCount; ++i) {
				vector[i] = (vector[i] - meansOfFeatures.get(i))
						/ stdevOfFeatures.get(i);
			}
			aPoint.setVector(vector);
		}
	}

	public DataPoint getPoint(int i) {
		return datapoints.get(i);
	}
	
	public MarkovTable makeMarkovTable(ArrayList<String> volumesToUse, double alpha) {
		MarkovTable markov = new MarkovTable(alpha, genres);
		
		for (String volume : volumesToUse) {
			int idx = volumeLabels.indexOf(volume);
			ArrayList<String> aGenreSequence = volumeGenres.get(idx);
			markov.trainSequence(aGenreSequence);
		}
		
		markov.interpolateProbabilities();
		markov.writeTable("/Users/tunder/output/markovtable.tsv");
		return markov;
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
	private ArrayList<String> folderIntersection(File[] featureFiles, File[] genreFiles) {
		
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
		System.out.println("Length " + hathiIDs.size());
		return hathiIDs;
	}

}
