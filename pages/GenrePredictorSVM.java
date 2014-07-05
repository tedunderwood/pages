/**
 * 
 */
package pages;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author tunder
 *
 */
public class GenrePredictorSVM extends GenrePredictor implements Serializable {
	private static final long serialVersionUID = 131L;
	private WekaDriverSVM theClassifier;
	
	public GenrePredictorSVM (GenreList genres, ArrayList<String> features, String genreToIdentify, 
			ArrayList<DataPoint> datapoints, String ridgeParameter, boolean verbose) {
		
		theClassifier = new WekaDriverSVM(genres, features, genreToIdentify, datapoints, ridgeParameter, verbose);
	}
	
	public GenrePredictorSVM (String dummyString) {
		theClassifier = new WekaDriverSVM(dummyString);
	}
	
	public double[][] testNewInstances(ArrayList<DataPoint> pointsToTest) {
		double[][] probabilities = theClassifier.testNewInstances(pointsToTest);
		return probabilities;
	}
}
