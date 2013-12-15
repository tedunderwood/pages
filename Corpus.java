package hmm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Corpus {

	static final String[] STRUCTURALFEATURES = { "posInVol", "lineLengthRatio",
			"capRatio", "wordLengthRatio", "distanceFromMid", "typeToken" };
	static final int FEATURESADDED = 6;
	static final String[][] CONVERSIONS = { { "colop", "back" },
			{ "epigr", "front" }, { "trv", "non" }, { "ora", "non" }, {"notes", "non"},
			{ "argum", "non" }, { "errat", "back" }, { "toc", "front" },
			{ "title", "front" }, { "impri", "front" },
			{ "gloss", "back" }, {"subsc", "catal"} };
	ArrayList<DataPoint> datapoints;
	ArrayList<ArrayList<String>> volumeGenres;
	ArrayList<String> volumeLabels;
	int numPoints;
	GenreList genres;
	static ArrayList<String> features;
	static int featureCount;
	static ArrayList<Double> stdevOfFeatures;
	static ArrayList<Double> meansOfFeatures;
	Vocabulary vocabulary;

	public Corpus(String wordcountFile, String genreFile, int featureCount) {

		genres = new GenreList();

		LineReader genreSource = new LineReader(genreFile);
		String[] filelines = genreSource.readlines();

		String currentVolume = "";
		volumeGenres = new ArrayList<ArrayList<String>>();
		volumeLabels = new ArrayList<String>();
		int pageInVol = 0;
		ArrayList<String> aGenreSequence = new ArrayList<String>();

		for (String line : filelines) {
			String[] tokens = line.split("\t");
			int tokenCount = tokens.length;
			if (tokenCount != 3) {
				System.out.println("Error: tokenCount not equal to 3 at "
						+ line);
				// not the world's most sophisticated error handling here
				// TODO: define Exception handling for input format issues
				continue;
			}
			String htid = tokens[0];
			int pagenum = Integer.parseInt(tokens[1]);
			String genre = tokens[2];
			genre = normalizeGenre(genre);
			if (!genres.genreLabels.contains(genre)) {
				genres.addLabel(genre);
			}
			assert (pagenum == pageInVol);

			if (currentVolume.equals("")) {
				// This is the drill when encountering any new volume.
				// If there was no previous volume, do this alone.
				aGenreSequence = new ArrayList<String>();
				currentVolume = htid;
				volumeLabels.add(htid);
			}

			if (htid.equals(currentVolume)) {
				// If the volume is not new, just extend aGenreSequence.
				// Note that this will also happen on the very first iteration
				// of the loop.
				aGenreSequence.add(genre);
				pageInVol += 1;
			} else {
				// In this case we're encountering a new volume, and there was
				// a previous volume.
				currentVolume = htid;
				volumeLabels.add(htid);
				volumeGenres.add(aGenreSequence);
				aGenreSequence = new ArrayList<String>();
				aGenreSequence.add(genre);
				pageInVol = 0;
			}
		}

		volumeGenres.add(aGenreSequence);
		// cleaning up after the last loop iteration, since there is no
		// "next volume" to push it.

		vocabulary = new Vocabulary(wordcountFile, featureCount);
		vocabulary.countWords();

		// The Vocabulary counts all words in the data and identifies
		// a subset of most-common words. It can now tell us whether
		// a given word is in that subset.

		HashMap<String, Integer> vocabularyMap = vocabulary.getMap();

		BlockReader mapper = new BlockReader(wordcountFile);

		// The mapper takes a text file and breaks it into
		// key, value pairs where the key is a docid,
		// and the value is the other 4 fields on the same line.

		ArrayList<Volume> volumes = mapper.mapVolumes(vocabulary);

		// The Volume objects will reduce all the lines associated with a
		// single docid. When they do this they produce DataPoints that can
		// either represent volumes or individual pages. I've implemented
		// these alternatives as two different methods of the Volume class.

		// In this implementation of Corpus, we create datapoints for each page
		// that are then
		// sorted and grouped by genre.

		// We're producing page points.
		numPoints = 0;
		datapoints = new ArrayList<DataPoint>();
		int minsize = 0;

		for (Volume thisVol : volumes) {
			System.out.println(thisVol.volumeID);
			ArrayList<DataPoint> newPoints = thisVol
					.makePagePoints(vocabularyMap);
			int volIndex = volumeLabels.indexOf(thisVol.volumeID);
			ArrayList<String> genresOfThisVol = volumeGenres.get(volIndex);
			if (genresOfThisVol.size() != newPoints.size()) {
				System.out.println("Genres: " + genresOfThisVol.size() + "but points: " + newPoints.size());
				minsize = Math.min(genresOfThisVol.size(), newPoints.size());
			}
			else {
				System.out.println(newPoints.size());
				minsize = newPoints.size();
			}
			// Hacky solution to errors in the data.
			// TODO: fix.

			for (int i = 0; i < minsize; ++i) {
				String genre = genresOfThisVol.get(i);
				DataPoint aPoint = newPoints.get(i);
				aPoint.setGenre(genre);
				datapoints.add(aPoint);
				numPoints += 1;
			}
		}

		newNormalizeFeatures(vocabulary, true);
		// true because this is a training corpus
		System.out.println(genres.genreLabels);

	}
	
	public Corpus(String wordcountFile, int featureCount, Vocabulary vocab) {

		this.vocabulary = vocab;

		// In this builder of corpora, we assume a) that we only have
		// wordcounts and don't yet know the genres and b) a vocabulary
		// for the model has already been established.

		HashMap<String, Integer> vocabularyMap = vocabulary.getMap();

		BlockReader mapper = new BlockReader(wordcountFile);

		// The mapper takes a text file and breaks it into
		// key, value pairs where the key is a docid,
		// and the value is the other 4 fields on the same line.

		ArrayList<Volume> volumes = mapper.mapVolumes(vocabulary);

		// In this implementation of Corpus, we should actually
		// only have one volume in our data.
		
		if (volumes.size() > 1) {
			System.out.println("Error: multiple volumes in file.");
		}
		
		Volume thisVol = volumes.get(0);

		// We're producing page points.
		numPoints = 0;
		
		System.out.println(thisVol.volumeID);
		datapoints = thisVol.makePagePoints(vocabularyMap);
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
		for (String[] row : CONVERSIONS) {
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
	 */

	private void normalizeFeatures(Vocabulary vocabulary) {
		String[] vocabularyList = vocabulary.vocabularyList;
		int vocabSize = vocabularyList.length;
		double[] vocabMeans = vocabulary.meanFreqOfWords;
		featureCount = vocabSize + FEATURESADDED;
		
		features = new ArrayList<String>(featureCount);
		meansOfFeatures = new ArrayList<Double>(featureCount);
		stdevOfFeatures = new ArrayList<Double>(featureCount);
		for (int i = 0; i < vocabSize; ++i) {
			features.add(vocabularyList[i]);
			meansOfFeatures.add(vocabMeans[i]);
		}
		
		for (String aFeature : STRUCTURALFEATURES) {
			features.add(aFeature);
		}

		for (int i = vocabSize; i < featureCount; ++i) {
			double sum = 0d;
			for (DataPoint aPoint : datapoints) {
				sum += aPoint.getVector()[i];
			}
			meansOfFeatures.add(sum / numPoints);
		}

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
		String[] vocabularyList = vocabulary.vocabularyList;
		int vocabSize = vocabularyList.length;
		
		
		if (trainingCorpus) {
			features = new ArrayList<String>(vocabSize + FEATURESADDED);
			meansOfFeatures = new ArrayList<Double>(vocabSize + FEATURESADDED);
			stdevOfFeatures = new ArrayList<Double>(vocabSize + FEATURESADDED);
			
			// Create the list of features.
			
			for (int i = 0; i < vocabSize; ++i) {
				features.add(vocabularyList[i]);
			}
			for (String aFeature : STRUCTURALFEATURES) {
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
	
			this.featureCount = vocabSize + FEATURESADDED;
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
		markov.writeTable("/Users/tunderwood/uniquefiction/markovtable.tsv");
		return markov;
	}

}
