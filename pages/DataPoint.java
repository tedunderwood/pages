package pages;

/**
 * @author tunderwood
 *
 * A fuller implementation of this class will have more getter and setter methods.
 * At the moment, I'm just treating this as a data object that pairs a label with
 * a vector of feature values.
 * 
 */

public class DataPoint {
	public String label;
	public String volume;
	public int page;
	public double[] vector;
	public int dimensionality;
	double magnitude;
	// we define this as the L2 norm or Euclidean length
	public String genre;
	
	public DataPoint(String label, double[] vector){
		this.label = label;
		String[] parts = label.split(",");
		this.volume = parts[0];
		if (parts.length > 1) {
			this.page = Integer.parseInt(parts[1]);
		}
		this.vector = vector;
		dimensionality = vector.length;
		magnitude = 0;
		for (int i = 0; i < dimensionality; ++ i) {
			magnitude = magnitude + Math.pow(vector[i], 2);
		}
		magnitude = Math.sqrt(magnitude);
		genre = "";
		
	}
	
	public double[] getVector() {
		return vector;
	}
	
	public void setVector(double[] newVector) {
		if (dimensionality == newVector.length) {
			this.vector = newVector;
		}
		else {
			System.out.println("Dimensionality mismatch.");
		}
	}
	
	public void setGenre(String newGenre) {
		this.genre = newGenre;
	}
	
	public void normalizeLength() {
		for (int i = 0; i < dimensionality; ++i) {
			vector[i] = vector[i] / magnitude;
		}
	}

}