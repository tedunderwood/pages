/**
 * 
 */
package pages;

import java.util.ArrayList;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 * @author tunder
 *
 */
public class WekaDriverMulticlass implements java.io.Serializable {
	Classifier forest;
	Instances trainingSet;
	FastVector featureNames;
	int numFeatures;
	int numInstances;
	int numGenres;
	String classLabel;
	double[][] memberProbs;
	
	private static final long serialVersionUID = 163L;
	
	public WekaDriverMulticlass (GenreList genres, ArrayList<String> features, ArrayList<DataPoint> datapoints, boolean verbose) {
		numFeatures = features.size();
		numInstances = datapoints.size();
		numGenres = genres.getSize();
		memberProbs = new double[numInstances][numGenres];
		
		String outpath = "/Users/tunder/output/classifiers/multiclass.txt";
		
		LineWriter writer = new LineWriter(outpath, true);
		
		featureNames = new FastVector(numFeatures + 1);
		for (int i = 0; i < numFeatures; ++ i) {
			Attribute a = new Attribute(features.get(i));
			featureNames.addElement(a);
		}
		
		// Now we add the class attribute.
		FastVector classValues = new FastVector(numGenres);
		for (String genre : genres.genreLabels) {
			classValues.addElement(genre);
		}
		Attribute classAttribute = new Attribute("ClassAttribute", classValues);
		featureNames.addElement(classAttribute);
		
		trainingSet = new Instances("multiclassForest", featureNames, numInstances);
		trainingSet.setClassIndex(numFeatures);
		
		int poscount = 0;
		for (DataPoint aPoint : datapoints) {
			Instance instance = new Instance(numFeatures + 1);
			for (int i = 0; i < numFeatures; ++i) {
				instance.setValue((Attribute)featureNames.elementAt(i), aPoint.vector[i]);
			}
	
			instance.setValue((Attribute)featureNames.elementAt(numFeatures), aPoint.genre);
			trainingSet.add(instance);
		}
		
		System.out.println("Forest: multiclass.");
		
		try {
			String[] options = {"-I", "110", "-K", "22"};
			forest = Classifier.forName("weka.classifiers.trees.RandomForest", options);
			forest.buildClassifier(trainingSet);
			if (verbose) {
				writer.print(forest.toString());
			}
			 
			Evaluation eTest = new Evaluation(trainingSet);
			eTest.evaluateModel(forest, trainingSet);
			 
			String strSummary = eTest.toSummaryString();
			if (verbose) {
				writer.print(strSummary);
			}
			
			// Get the confusion matrix
			String strMatrix = eTest.toMatrixString();
			if (verbose) {
				writer.print(strMatrix);
			}
		}
		catch (Exception e){
			e.printStackTrace();
			System.out.println(e);
		}
		if (verbose) {
			writer.print("\n\n");
		}
		
	}
	
	public double[][] getPredictions() {
		return memberProbs;
	}
	
	public double[][] testNewInstances(ArrayList<DataPoint> pointsToTest) {

		String genreToIdentify = classLabel;
		int testSize = pointsToTest.size();
		double[][] testProbs = new double[testSize][2];
		
		ArrayList<Instance> testSet = new ArrayList<Instance>(testSize);
		
		for (DataPoint aPoint : pointsToTest) {
			Instance instance = new Instance(numFeatures + 1);
			instance.setDataset(trainingSet);
			for (int i = 0; i < numFeatures; ++i) {
				instance.setValue((Attribute)featureNames.elementAt(i), aPoint.vector[i]);
			}
			if (aPoint.genre.equals(genreToIdentify)) {
				instance.setValue((Attribute)featureNames.elementAt(numFeatures), "positive");
			}
			else {
				instance.setValue((Attribute)featureNames.elementAt(numFeatures), "negative");
			}
			testSet.add(instance);
		}
		
		try{
			for (int i = 0; i < testSize; ++i) {
				Instance anInstance = testSet.get(i);
				testProbs[i] = forest.distributionForInstance(anInstance);
				System.out.println(i);
			}
		}
		catch (Throwable t) {
			t.printStackTrace();
			System.out.println(t);
		}
		
		return testProbs;
	}
}
