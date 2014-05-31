package pages;

import java.util.ArrayList;

public class TrainingThread implements Runnable {
	
	private GenreList genres;
	private ArrayList<String> features;
	private String genreToIdentify;
	private ArrayList<DataPoint> datapoints;
	private String ridgeParameter;
	private boolean verbose;
	public WekaDriver classifier;
	

	public TrainingThread(GenreList genres, ArrayList<String> features, String genreToIdentify, 
			ArrayList<DataPoint> datapoints, String ridgeParameter, boolean verbose) {
		this.genres = genres;
		this.features = features;
		this.genreToIdentify = genreToIdentify;
		this.datapoints = datapoints;
		this.ridgeParameter = ridgeParameter;
		this.verbose = verbose;	
	}

	@Override
	public void run() {
		if (genreToIdentify.equals("dummy")) {
			this.classifier = new WekaDriver("dummy");
		}
		else {
			this.classifier = new WekaDriver(genres, features, genreToIdentify, datapoints, ridgeParameter, verbose);
		}
	}

}
