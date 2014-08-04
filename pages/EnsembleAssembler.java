/**
 * 
 */
package pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author tunder
 *
 */
public class EnsembleAssembler implements Runnable {
	private Model model;
	private String name;
	private String modelType;
	private ArrayList<String> genres;
	private int numVolumes;
	private final BlockingQueue<Unknown> inQueue;
	private final BlockingQueue<Unknown> outQueue;
	
	public EnsembleAssembler(Model model, String modelName, String modelInstruction, int numVolumes, 
			BlockingQueue<Unknown> inQueue, BlockingQueue<Unknown> outQueue) {
		
		this.model = model;
		this.name = modelName;
		this.modelType = modelInstruction;
		this.numVolumes = numVolumes;
		this.inQueue = inQueue;
		this.outQueue = outQueue;
	}
	
	@Override
	public void run() {
		
		try {
			for (int i = 0; i < numVolumes; ++i) {
				Unknown volume = classifyVolume(inQueue.poll(10, TimeUnit.MINUTES));
				outQueue.offer(volume, 10, TimeUnit.MINUTES);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		System.out.println("Terminated loop, model " + name);
	}
	
	private Unknown classifyVolume(Unknown beingClassified) {
			
		Vocabulary vocabulary = model.vocabulary;
		MarkovTable markov = model.markov;
		this.genres = model.genreList.genreLabels;
		FeatureNormalizer normalizer = model.normalizer;
		ArrayList<GenrePredictor> classifiers = model.classifiers;
		int numGenres = genres.size();
		
		Corpus thisVolume = new Corpus(beingClassified.getLines(), beingClassified.getLabel(), vocabulary, normalizer);
		beingClassified.putNumPoints(thisVolume.numPoints);
		int numPoints = thisVolume.numPoints;
		String volLabel = thisVolume.getFirstVolID();
		
		if (numPoints < 1) {
			WarningLogger.logWarning(volLabel + " was found to have zero pages!");
			return beingClassified;
		}
		
		ArrayList<double[]> rawProbs;
		if (modelType.equals("-multiclassforest")) {
			GenrePredictorMulticlass forest = (GenrePredictorMulticlass) classifiers.get(0);
			rawProbs = forest.getRawProbabilities(thisVolume, numPoints);
		}
		else {
			// We assume model type is -onevsalllogistic.
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
		ClassificationResult smoothResult = new ClassificationResult(smoothedProbs, numGenres, genres);
			
		beingClassified.putRaw(rawResult);
		beingClassified.putSmooth(smoothResult);
		
		return beingClassified;	
	}
	
}
