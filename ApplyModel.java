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
	
	private static void crossValidate(int folds, String ridge){
		// start by dividing the corpus into subsets
		SubdividedCorpus subsets = new SubdividedCorpus(corpus, folds);
		ArrayList<String> allPredictions = new ArrayList<String>(numInstances);
		ArrayList<DataPoint> pointsInTestingOrder = new ArrayList<DataPoint>(numInstances);
		ArrayList<String> allSmoothedPredictions = new ArrayList<String>(numInstances);
		
		for (int leaveout = 0; leaveout < folds; ++leaveout) {
			System.out.println("PARTITION " +  leaveout);
			ArrayList<DataPoint> trainingCorpus = subsets.pointsExcluding(leaveout);
			
			ArrayList<DataPoint> testCorpus = subsets.pointsOnly(leaveout);
			int testSize = testCorpus.size();
			pointsInTestingOrder.addAll(testCorpus);
			
			ArrayList<double[]> classProbs = new ArrayList<double[]>(testSize);
			for (int i = 0; i < testSize; ++i) {
				double[] probs = new double[numGenres];
				Arrays.fill(probs, 0);
				classProbs.add(probs);
			}
			
			for (int i = 2; i < numGenres; ++i) {
				String aGenre = genres.get(i);
				WekaDriver classify = new WekaDriver(corpus.genres,
						Corpus.features, aGenre, trainingCorpus, ridge, false);
				double[][] probs = classify.testNewInstances(testCorpus);
				for (int j = 0; j < testSize; ++j) {
					classProbs.get(j)[i] = probs[j][0];
				}
			}
			
			ArrayList<String> foldPredictions = interpretEvidence(classProbs);
			assessPredictions(foldPredictions, testCorpus);
			allPredictions.addAll(foldPredictions);
			
			ArrayList<String> trainingVolumes = subsets.volumesExcluding(leaveout);
			MarkovTable markov = corpus.makeMarkovTable(trainingVolumes, MARKOVSMOOTHING);
			ArrayList<String> smoothedPredictions = smooth(classProbs, testCorpus, markov);
			allSmoothedPredictions.addAll(smoothedPredictions);
		}
		System.out.println("WHOLE CORPUS:");
		assessPredictions(allPredictions, pointsInTestingOrder);
		System.out.println("WHOLE CORPUS SMOOTHED:");
		assessPredictions(allSmoothedPredictions, pointsInTestingOrder);
		evaluateAccuracy(allPredictions, pointsInTestingOrder, folds, "RAW VALUES:");
		evaluateAccuracy(allSmoothedPredictions, pointsInTestingOrder, folds, "MARKOV-SMOOTHED PREDICTIONS:");
	}
	
	private static ArrayList<String> smooth(ArrayList<double[]> rawProbs, ArrayList<DataPoint> points, MarkovTable markov) {
		int numPages = points.size();
		int pagecounter = -1;
		String volume = "";
		String newvolume = "";
		ArrayList<double[]> volumeEvidence = new ArrayList<double[]>();
		ArrayList<String> smoothedPredictions = new ArrayList<String>(
				numPages);

		// Each row of the ArrayList contains a vector of predictions
		// about a single page. Each cell of that vector predicts
		// the probability that page p belongs to genre g.

		for (int i = 0; i < numPages; ++i) {
			DataPoint aPoint = points.get(i);
			int page = aPoint.page;
			newvolume = aPoint.volume;
			if (volume.equals(newvolume)) {
				if (page <= pagecounter) {
					System.out.println("Error condition at " + volume + ", "
							+ page + "duplicated.");
				}
				pagecounter = page;
				volumeEvidence.add(rawProbs.get(i));
				// this is the normal order of things when we're inside a volume
			} else {
				// this is the normal order of things when we hit a new volume
				volume = newvolume;
				System.out.println("Transition to " + newvolume + " at page "
						+ pagecounter);
				pagecounter = page;
				if (i > 0) {
					// don't do this on the very first pass!
					ArrayList<double[]> smoothed = ForwardBackward.smooth(
							volumeEvidence, markov);
					smoothedPredictions.addAll(interpretEvidence(smoothed));
					volumeEvidence.clear();
				}
				volumeEvidence.add(rawProbs.get(i));
			}
		}
		// Push last volume.
		ArrayList<double[]> smoothed = ForwardBackward.smooth(volumeEvidence,
				markov);
		smoothedPredictions.addAll(interpretEvidence(smoothed));
		
		System.out.println("SIZE OF SMOOTHED PREDICTIONS "
				+ smoothedPredictions.size());
		System.out.println("ASSESSING SMOOTHED PREDICTIONS:");
		assessPredictions(smoothedPredictions, points);
		
		return smoothedPredictions;
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

	private static void assessPredictions(ArrayList<String> predictions,
			ArrayList<DataPoint> datapoints) {
		int correct = 0;
		int wrong = 0;
		int predictionSize = predictions.size();
		for (int i = 0; i < predictionSize; ++i) {
			String predicted = predictions.get(i);
			String actual = datapoints.get(i).genre;
			if (genresAreEqual(predicted, actual)) {
				correct += 1;
			} else {
				wrong += 1;
			}
		}
		System.out.println("Correct: " + correct);
		System.out.println("Wrong: " + wrong);
	}

	private static void selfsmooth(ArrayList<double[]> rawProbs, Corpus corpus, MarkovTable markov) {

		ArrayList<String> rawPredictions = interpretEvidence(rawProbs);
		System.out.println("ASSESSING RAW PREDICTIONS:");
		
		ArrayList<DataPoint> datapoints = corpus.datapoints;
		assessPredictions(rawPredictions, datapoints);

		int pagecounter = -1;
		String volume = "";
		String newvolume = "";
		ArrayList<double[]> evidenceMatrix = new ArrayList<double[]>();
		ArrayList<String> smoothedPredictions = new ArrayList<String>(
				numInstances);

		// Each row of the ArrayList contains a vector of predictions
		// about a single page. Each cell of that vector predicts
		// the probability that page p belongs to genre g.

		for (int i = 0; i < numInstances; ++i) {
			DataPoint aPoint = datapoints.get(i);
			int page = aPoint.page;
			newvolume = aPoint.volume;
			if (volume.equals(newvolume)) {
				if (page <= pagecounter) {
					System.out.println("Error condition at " + volume + ", "
							+ page + "duplicated.");
				}
				pagecounter = page;
				evidenceMatrix.add(rawProbs.get(i));
				// this is the normal order of things when we're inside a volume
			} else {
				// this is the normal order of things when we hit a new volume
				volume = newvolume;
				System.out.println("Transition to " + newvolume + " at page "
						+ pagecounter);
				pagecounter = page;
				if (i > 0) {
					// don't do this on the very first pass!
					ArrayList<double[]> smoothed = ForwardBackward.smooth(
							evidenceMatrix, markov);
					smoothedPredictions.addAll(interpretEvidence(smoothed));
					evidenceMatrix.clear();
				}
				evidenceMatrix.add(rawProbs.get(i));
			}
		}
		// Push last volume.
		ArrayList<double[]> smoothed = ForwardBackward.smooth(evidenceMatrix,
				markov);
		smoothedPredictions.addAll(interpretEvidence(smoothed));

		System.out.println("SIZE OF SMOOTHED PREDICTIONS "
				+ smoothedPredictions.size());
		System.out.println("ASSESSING SMOOTHED PREDICTIONS:");
		assessPredictions(smoothedPredictions, datapoints);
		LineWriter writer = new LineWriter(
				"/Users/tunderwood/Eclipse/StandardOut.txt", true);

		if (smoothedPredictions.size() < numInstances) {
			numInstances = smoothedPredictions.size();
		}
		// cheap hack.

		String[] outlines = new String[numInstances];
		for (int i = 0; i < numInstances; ++i) {
			outlines[i] = datapoints.get(i).label + "\t"
					+ rawPredictions.get(i) + "\t" + smoothedPredictions.get(i)
					+ "\t" + corpus.getPoint(i).genre;
		}
		writer.send(outlines);
	}
	
	private static void evaluateAccuracy(ArrayList<String> predicted,
			ArrayList<DataPoint> realPoints, int folds, String header) {
		
		LineWriter writer = new LineWriter("/Users/tunderwood/Eclipse/accuracy.txt", true);
		writer.print(header);
		writer.print("Dataset with " + numInstances + " datapoints.");
		writer.print("Number of features: " + featureCount );
		writer.print("Ridge parameter: " + ridge );
		
		int setsize = realPoints.size();
		ArrayList<String> actual = new ArrayList<String>(setsize);
		for (int i = 0; i < setsize; ++i) {
			DataPoint aPoint = realPoints.get(i);
			actual.add(aPoint.genre);
		}
		
		ArrayList<String> alreadyIncluded = new ArrayList<String>();
		alreadyIncluded.add("begin");
		alreadyIncluded.add("end");
		// we don't want those included in the evaluation
		ArrayList<String> sparsegenrelist = new ArrayList<String>();
		
		for (int i = 0; i < numGenres; ++i) {
			String genre = genres.get(i);
			if (alreadyIncluded.contains(genre)) continue;
			alreadyIncluded.add(genre);
			for (String[] row : EQUIVALENT) {
				if (Arrays.asList(row).contains(genre)) {
					alreadyIncluded.addAll(Arrays.asList(row));
				}
			}
			sparsegenrelist.add(genre);
		}
		
		int numSparseGenres = sparsegenrelist.size();
		ArrayList<double[]> evaluations = new ArrayList<double[]>(numSparseGenres);
		int microtruepositives = 0;
		int microtruenegatives = 0;
		int microfalsepositives = 0;
		int microfalsenegatives = 0;
		
		for (int i = 0; i < numSparseGenres; ++i) {
			int truepositives = 0;
			int truenegatives = 0;
			int falsepositives = 0;
			int falsenegatives = 0;
			String thisgenre = sparsegenrelist.get(i);

			for (int j = 0; j < setsize; ++j) {
				String thisprediction = predicted.get(j);
				String thisactual = actual.get(j);
				if (genresAreEqual(thisactual,thisgenre)) {
					if (genresAreEqual(thisprediction, thisgenre))
						truepositives += 1;
					else
						falsenegatives += 1;
				} else {
					if (genresAreEqual(thisprediction, thisgenre))
						falsepositives += 1;
					else
						truenegatives += 1;
				}
			}
			
			microtruepositives += truepositives;
			microtruenegatives += truenegatives;
			microfalsenegatives += falsenegatives;
			microfalsepositives += falsepositives;

			double accuracy = (truepositives + truenegatives)
					/ (double) (truepositives + falsepositives + truenegatives + falsenegatives);
			double precision = truepositives / (double) (truepositives + falsepositives + 0.0001d);
			double recall = truepositives / (double) (truepositives + falsenegatives + 0.0001d);
			double fmeasure = (2 * precision * recall) / (precision + recall + 0.00001d);
			String outline = thisgenre + " fp " + falsepositives + " tp " + truepositives + " fn " + falsenegatives + " tn " + truenegatives;
			writer.print(outline);
			outline = thisgenre + " accuracy " + accuracy + " precision " + precision + " recall " + recall + " fmeasure " + fmeasure;
			writer.print(outline);
			System.out.println(outline);
			double[] row = {accuracy, precision, recall, fmeasure};
			evaluations.add(row);
		}
		
		double accuracy = 0;
		double precision = 0;
		double recall = 0;
		double fmeasure = 0;
		
		for (int i = 0; i < numSparseGenres; ++i) {
			double[] row = evaluations.get(i);
			accuracy += row[0];
			precision += row[1];
			recall += row[2];
			fmeasure += row[3];
		}
		
		accuracy = accuracy / numSparseGenres;
		precision = precision / numSparseGenres;
		recall = recall / numSparseGenres;
		fmeasure = fmeasure / numSparseGenres;
		
		writer.print("\nMacro-averaged statistics:\n");
		writer.print("Accuracy: " + accuracy);
		writer.print("Precision: " + precision);
		writer.print("Recall: " + recall);
		writer.print("F-measure: " + fmeasure);
		writer.print("\n");
		
		double microaverageAcc = (microtruepositives + microtruenegatives)
				/ (double) (microtruepositives + microfalsepositives + microtruenegatives + microfalsenegatives);
		double microaveragePre = microtruepositives / (double) (microtruepositives + microfalsepositives + 0.0001d);
		double microaverageRe = microtruepositives / (double) (microtruepositives + microfalsenegatives + 0.0001d);
		double microFmeasure = (2 * microaveragePre * microaverageRe) / (microaveragePre + microaveragePre + 0.00001d);
		
		writer.print("Micro-averaged statistics:\n");
		writer.print("Accuracy: " + microaverageAcc);
		writer.print("Precision: " + microaveragePre);
		writer.print("Recall: " + microaverageRe);
		writer.print("F-measure: " + microFmeasure);
		writer.print("\n");
	}

}
