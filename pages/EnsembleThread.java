/**
 * 
 */
package pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import json.JSONArray;
import json.JSONObject;

/**
 * Not currently implemented as a "thread," so the class name is misleading. But it could be,
 * and probably will be, so implemented.
 * @author tunder
 *
 */
public class EnsembleThread {
	
	private ArrayList<String> genres;
	private int numPoints;
	private int numModels;
	private int numGenres;
	private HashMap<String, Integer> genreIndex;
	
	public EnsembleThread(String thisFile, String inputDir, String outputDir, ArrayList<Model> theEnsemble, 
			ArrayList<String> modelNames, ArrayList<String> modelInstructions, boolean isPairtree) {
		
		numModels = theEnsemble.size();
		assert (numModels == modelNames.size());
		assert (numModels == modelInstructions.size());
		
		ArrayList<String> filelines;
		
		if (isPairtree) {
			PairtreeReader reader = new PairtreeReader(inputDir);
			filelines = reader.getVolume(thisFile);
		}
		else {
			String volumePath = inputDir + thisFile + ".pg.tsv";
			LineReader fileSource = new LineReader(volumePath);
			try {
				filelines = fileSource.readList();
			}
			catch (InputFileException e) {
				WarningLogger.addFileNotFound(thisFile);
				return;
			}
		}
		
		ArrayList<ClassificationResult> allRawResults = new ArrayList<ClassificationResult>(numModels);
		ArrayList<ClassificationResult> allSmoothedResults = new ArrayList<ClassificationResult>(numModels);
		String volLabel = "error";
		
		for (int m = 0; m < numModels; ++m) {
			
			Model model = theEnsemble.get(m);
			String name = modelNames.get(m);
			String modelType = modelInstructions.get(m);
			
			Vocabulary vocabulary = model.vocabulary;
			MarkovTable markov = model.markov;
			this.genres = model.genreList.genreLabels;
			FeatureNormalizer normalizer = model.normalizer;
			ArrayList<GenrePredictor> classifiers = model.classifiers;
			numGenres = genres.size();
			this.genreIndex = model.genreList.genreIndex;
			
			Corpus thisVolume = new Corpus(filelines, thisFile, vocabulary, normalizer);
			numPoints = thisVolume.numPoints;
			volLabel = thisVolume.getFirstVolID();
			
			if (numPoints < 1) {
				WarningLogger.logWarning(thisFile + " was found to have zero pages!");
				return;
			}
			
			ArrayList<double[]> rawProbs;
			if (modelType.equals("-multiclassforest")) {
				GenrePredictorMulticlass forest = (GenrePredictorMulticlass) classifiers.get(0);
				rawProbs = forest.getRawProbabilities(thisVolume, numPoints);
			}
			else {
				// We assume model type is -onevsall.
				ArrayList<DataPoint> thesePages = thisVolume.datapoints;
				rawProbs = new ArrayList<double[]>(numPoints);
				for (int i = 0; i < numPoints; ++i) {
					double[] probs = new double[numGenres];
					Arrays.fill(probs, 0);
					rawProbs.add(probs);
				}
				
				for (int i = 2; i < numGenres; ++i) {
					GenrePredictor classify = classifiers.get(i);
					// System.out.println(classify.reportStatus());
					double[][] probs = classify.testNewInstances(thesePages);
					for (int j = 0; j < numPoints; ++j) {
						rawProbs.get(j)[i] = probs[j][0];
					}
				}
			}
				
			ArrayList<double[]> smoothedProbs = ForwardBackward.smooth(rawProbs, markov);
	
			ClassificationResult rawResult = new ClassificationResult(rawProbs, numGenres, genres);
			ClassificationResult smoothedResult = new ClassificationResult(smoothedProbs, numGenres, genres);
			
			String outFile = thisFile + ".predict";
			String outPath = outputDir + "/" + outFile;
			
			writeJSON(outPath, thisVolume, rawResult, smoothedResult, rawProbs, smoothedProbs, name);
			allRawResults.add(rawResult);
			allSmoothedResults.add(smoothedResult);
		}
		
		ClassificationResult consensus = reconcilePredictions(allRawResults, allSmoothedResults);
		writeConsensus(outputDir, volLabel, consensus);
		
	}
	
	private ClassificationResult reconcilePredictions(ArrayList<ClassificationResult> rawResults, 
			ArrayList<ClassificationResult> smoothedResults) {
		
		// One strategy for combining ensembles is to average their predicted probabilities
		// for each genre. In practice, simple voting is often more reliable, and we
		// use that as our main strategy. But averaging probabilities is useful
		// for other purposes. It gives us probabilistic output, and we use it to generate
		// tiebreakers in the voting process. We only use smoothed probabilities here.
		
		ArrayList<double[]> averagePredictions = new ArrayList<double[]>(numPoints);
		ArrayList<String> meanGenres = new ArrayList<String>(numPoints);
		
		for (int i = 0; i < numPoints; ++i) {
			double[] thisPage = new double[numGenres];
			Arrays.fill(thisPage, 0d);
			for (int j = 0; j < numModels; ++j) {
				double[] thisPrediction = smoothedResults.get(j).probabilities.get(i);
				// That's a list of genre probabilities for page i produced by model j.
				thisPrediction = normalize(thisPrediction);
				// Sum all predictions for this page.
				for (int k = 0; k < numGenres; ++ k) {
					thisPage[k] += thisPrediction[k];
				}
			}
			thisPage = normalize(thisPage);
			String topGenreByAveraging = maxgenre(thisPage);
			averagePredictions.add(thisPage);
			meanGenres.add(topGenreByAveraging);
		}
		
		// Now for the voting. We allow both rough and smooth models to vote. The smoothed
		// are ultimately preferred, since they've generated the tiebreakers.
		
		ArrayList<String> consensus = new ArrayList<String>(numPoints);
		
		for (int i = 0; i < numPoints; ++i) {
			int[] theseVotes = new int[numGenres];
			Arrays.fill(theseVotes, 0);
			for (int j = 0; j < numModels; ++j) {
				String roughPrediction = rawResults.get(j).predictions.get(i);
				String smoothPrediction = smoothedResults.get(j).predictions.get(i);
				addVote(theseVotes, roughPrediction);
				addVote(theseVotes, smoothPrediction);
			}
			consensus.add(runElection(theseVotes, meanGenres.get(i)));
			// The second argument there is a tiebreaker generated by numeric
			// averaging of smoothed predictions.	
		}
		
		ClassificationResult consensusResult = new ClassificationResult(averagePredictions, consensus, numGenres);
		return consensusResult;
	}
	
	private void writeJSON(String outPath, Corpus thisVolume, ClassificationResult rawResult, ClassificationResult smoothedResult,
			ArrayList<double[]> smoothedProbs, ArrayList<double[]> rawProbs, String modelLabel) {
		
		ArrayList<String> rawPredictions = rawResult.predictions;
		ArrayList<String> predictions = smoothedResult.predictions;
		
		ArrayList<JSONObject> predictionList = new ArrayList<JSONObject>(numPoints);
		for (int i = 0; i < numPoints; ++i) {
			JSONObject pageObject = new JSONObject();
			pageObject.put("raw", rawPredictions.get(i));
			pageObject.put("smoothed", predictions.get(i));
			double[] thisPageProbs = smoothedProbs.get(i);
			for (int j = 0; j < genres.size(); ++j) {
				String genre = genres.get(j);
				pageObject.put(genre, thisPageProbs[j]);
			}
			predictionList.add(pageObject);
		}
		
		JSONObject topObject = new JSONObject();
		topObject.put("VolID", thisVolume.getFirstVolID());
		topObject.put("model", modelLabel);
		JSONArray predictionArray = new JSONArray(predictionList);
		topObject.put("predictions", predictionArray);
		topObject.put("avgmaxprob", smoothedResult.averageMaxProb);
		topObject.put("avggap", smoothedResult.averageGap);
		
		LineWriter writer = new LineWriter(outPath, true);
		// The boolean flag here sets the writer to append mode.
		writer.print(topObject.toString());
		
	}
	
	private void writeConsensus(String outPath, String volLabel, ClassificationResult consensusResult) {
		
		ArrayList<String> predictions = consensusResult.predictions;
		ArrayList<double[]> probabilities = consensusResult.probabilities;
		
		ArrayList<JSONObject> predictionList = new ArrayList<JSONObject>(numPoints);
		for (int i = 0; i < numPoints; ++i) {
			JSONObject pageObject = new JSONObject();
			pageObject.put("consensus", predictions.get(i));
			double[] thisPageProbs = probabilities.get(i);
			for (int j = 0; j < genres.size(); ++j) {
				String genre = genres.get(j);
				pageObject.put(genre, thisPageProbs[j]);
			}
			predictionList.add(pageObject);
		}
		
		JSONObject topObject = new JSONObject();
		topObject.put("VolID", volLabel);
		topObject.put("model", "ensemble");
		JSONArray predictionArray = new JSONArray(predictionList);
		topObject.put("predictions", predictionArray);
		topObject.put("avgmaxprob", consensusResult.averageMaxProb);
		topObject.put("avggap", consensusResult.averageGap);
		
		LineWriter writer = new LineWriter(outPath, true);
		// The boolean flag here sets the writer to append mode.
		writer.print(topObject.toString());
	}
	
	private double[] normalize(double[] input) {
		double total = 0d;
		for (double element : input) {
			total += element;
		}
		for (int i = 0; i < input.length; ++ i) {
			input[i] = input[i] / total;
		}
		return input;
	}
	
	private String maxgenre(double[] predictions) {
		String theGenre = "error";
		double max = 0d;
		for (int i = 0; i < numGenres; ++i) {
			if (predictions[i] > max) {
				max = predictions[i];
				theGenre = genres.get(i);
			}
		}
		return theGenre;
	}
	
	private String maxvote(int[] votes) {
		// This assumes there are no ties!
		String theGenre = "error";
		int max = 0;
		for (int i = 0; i < numGenres; ++i) {
			if (votes[i] > max) {
				max = votes[i];
				theGenre = genres.get(i);
			}
		}
		return theGenre;
	}
	
	private void addVote(int[] votes, String aGenre) {
		int idx = genreIndex.get(aGenre);
		votes[idx] += 1;
	}
	
	private String runElection(int[] votes, String tiebreaker) {
		int[] sortedvotes = votes.clone();
		Arrays.sort(sortedvotes);
		// This sorted list won't tell us which genre is highest, but it
		// does tell us whether we have a tie or not.
		
		if (sortedvotes[numGenres - 1] > sortedvotes[numGenres - 2]) {
			// We have a clear winner. No tiebreaking necessary.
			return maxvote(votes);
		}
		
		else {
			addVote(votes, tiebreaker);
			sortedvotes = votes.clone();
			Arrays.sort(sortedvotes);
			
			if (sortedvotes[numGenres - 1] > sortedvotes[numGenres - 2]) {
				// Tiebreaking has produced a winner.
				return maxvote(votes);
			}
			else {
				return resolveTie(votes);
			}
		}
	}
	
	private String resolveTie(int[] votes) {
		String[] contenders = new String[2];
		Arrays.fill(contenders, "error");
		int max = 0;
		int secondmax = 0;
		for (int i = 0; i < numGenres; ++i) {
			if (votes[i] >= max) {
				secondmax = max;
				max = votes[i];
				contenders[1] = contenders[0];
				contenders[0] = genres.get(i);
			}
			else if (votes[i] >= secondmax) {
				secondmax = votes[i];
				contenders[1] = genres.get(i);
			}
		}
		
		return contenders[new Random().nextInt(2)];
	}
	
}
