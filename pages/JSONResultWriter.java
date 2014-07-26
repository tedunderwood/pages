/**
 * 
 */
package pages;

import java.util.ArrayList;
import java.text.DecimalFormat;
import json.JSONArray;
import json.JSONObject;

/**
 * @author tunder
 *
 */
public class JSONResultWriter {
	private ArrayList<String> genres;
	private String outPath;
	private String modelLabel;
	
	public JSONResultWriter(String outPath, String modelLabel, ArrayList<String> genres) {
		this.genres = genres;
		this.outPath = outPath;
		this.modelLabel = modelLabel;
	}
	
	public void writeJSON(Corpus thisVolume, ClassificationResult rawResult, ClassificationResult smoothedResult) {
		ArrayList<double[]> smoothedProbs = smoothedResult.probabilities;
		int numPoints = thisVolume.numPoints;
		ArrayList<String> rawPredictions = rawResult.predictions;
		ArrayList<String> smoothedPredictions = smoothedResult.predictions;
		DecimalFormat fourPlaces = new DecimalFormat("0.0###");
		
		assert (rawPredictions.size() == smoothedProbs.size());
		
		JSONArray predictionArray = new JSONArray();
		for (int i = 0; i < numPoints; ++i) {
			JSONObject pageObject = new JSONObject();
			double[] thisPageProbs = smoothedProbs.get(i);
			for (int j = 0; j < genres.size(); ++j) {
				String genre = genres.get(j);
			    String formattedString = fourPlaces.format(thisPageProbs[j]);
				pageObject.put(genre, formattedString);
			}
			predictionArray.put(pageObject);
		}
		
		JSONArray rawGenres = new JSONArray();
		for (String aGenre : rawPredictions) {
			rawGenres.put(aGenre);
		}
		JSONArray smoothedGenres = new JSONArray();
		for (String aGenre : smoothedPredictions) {
			smoothedGenres.put(aGenre);
		}
		
		JSONObject topObject = new JSONObject();
		topObject.put("volID", thisVolume.getFirstVolID());
		topObject.put("model", modelLabel);
		topObject.put("probabilities", predictionArray);
		topObject.put("rawPredictions", rawGenres);
		topObject.put("smoothedPredictions", smoothedGenres);
		topObject.put("avgMaxProb", smoothedResult.averageMaxProb);
		topObject.put("avgGap", smoothedResult.averageGap);
		
		LineWriter writer = new LineWriter(outPath, true);
		// The boolean flag here sets the writer to append mode.
		writer.print(topObject.toString());
		
	}
	
	public void writeConsensus(String volID, ClassificationResult consensusResult, int numPoints) {
		ArrayList<double[]> smoothedProbs = consensusResult.probabilities;
		ArrayList<String> smoothedPredictions = consensusResult.predictions;
		DecimalFormat fourPlaces = new DecimalFormat("0.0###");
		
		assert (smoothedPredictions.size() == smoothedProbs.size());
		
		JSONArray predictionArray = new JSONArray();
		for (int i = 0; i < numPoints; ++i) {
			JSONObject pageObject = new JSONObject();
			double[] thisPageProbs = smoothedProbs.get(i);
			for (int j = 0; j < genres.size(); ++j) {
				String genre = genres.get(j);
			    String formattedString = fourPlaces.format(thisPageProbs[j]);
				pageObject.put(genre, formattedString);
			}
			predictionArray.put(pageObject);
		}
		
		JSONArray smoothedGenres = new JSONArray();
		for (String aGenre : smoothedPredictions) {
			smoothedGenres.put(aGenre);
		}
		
		JSONObject topObject = new JSONObject();
		topObject.put("volID", volID);
		topObject.put("model", modelLabel);
		topObject.put("probabilities", predictionArray);
		topObject.put("smoothedPredictions", smoothedGenres);
		topObject.put("avgMaxProb", consensusResult.averageMaxProb);
		topObject.put("avgGap", consensusResult.averageGap);
		
		LineWriter writer = new LineWriter(outPath, true);
		// The boolean flag here sets the writer to append mode.
		writer.print(topObject.toString());
		
	}
}
