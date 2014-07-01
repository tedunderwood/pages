/**
 * 
 */
package pages;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * The base class GenrePredictor is an uninteresting wrapper for WekaDriver.
 * It passes everything it's given to WekaDriver and returns everything
 * WekaDriver returns. The reason for its existence is that it can be subclassed
 * to create more interesting patterns, notably GenrePredictorAllVsAll, which
 * implements the same behavior in a very different way.
 *
 */
public class GenrePredictor implements Serializable {
	private static final long serialVersionUID = 121L;
	private WekaDriver theClassifier;
	
	public GenrePredictor() {
		// purely for subclassing
	}
	
	public GenrePredictor (GenreList genres, ArrayList<String> features, String genreToIdentify, 
			ArrayList<DataPoint> datapoints, String ridgeParameter, boolean verbose) {
		
		theClassifier = new WekaDriver(genres, features, genreToIdentify, datapoints, ridgeParameter, verbose);
	}
	
	public GenrePredictor (String dummyString) {
		theClassifier = new WekaDriver(dummyString);
	}
	
	public double[][] testNewInstances(ArrayList<DataPoint> pointsToTest) {
		double[][] probabilities = theClassifier.testNewInstances(pointsToTest);
		return probabilities;
	}

}
