/**
 * 
 */
package pages;
import java.util.ArrayList;
import java.io.File;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instances;
import weka.core.Instance;

/**
 * @author tunderwood
 *
 */
public class WekaDriver {
	
	Classifier logistic;
	Instances trainingSet;
	FastVector featureNames;
	int numFeatures;
	int numInstances;
	String ridgeParameter;
	String classLabel;
	double[][] memberProbs;
	
	public WekaDriver (String genre) {
		// Returns a dummy class to fill an unused spot in the ArrayList.
		// TODO: Refactor so this is not necessary.
		this.classLabel = genre;
	}
	
	public WekaDriver (GenreList genres, ArrayList<String> features, String genreToIdentify, ArrayList<DataPoint> datapoints, String ridgeParameter, boolean verbose) {
		numFeatures = features.size();
		numInstances = datapoints.size();
		this.ridgeParameter = ridgeParameter;
		this.classLabel = genreToIdentify;
		memberProbs = new double[numInstances][2];
		
		String outpath = "/Users/tunder/output/" + classLabel;
		File existingVersion = new File(outpath);
		if (existingVersion.exists()) existingVersion.delete();
		
		LineWriter writer = new LineWriter(outpath, true);
		
		featureNames = new FastVector(numFeatures + 1);
		for (int i = 0; i < numFeatures; ++ i) {
			Attribute a = new Attribute(features.get(i));
			featureNames.addElement(a);
		}
		
		// Now we add the class attribute.
		FastVector classValues = new FastVector(2);
		classValues.addElement("positive");
		classValues.addElement("negative");
		Attribute classAttribute = new Attribute("ClassAttribute", classValues);
		featureNames.addElement(classAttribute);
		
		trainingSet = new Instances(genreToIdentify, featureNames, numInstances);
		trainingSet.setClassIndex(numFeatures);
		ArrayList<Instance> simpleListOfInstances = new ArrayList<Instance>(numInstances);
		
		int poscount = 0;
		for (DataPoint aPoint : datapoints) {
			Instance instance = new Instance(numFeatures + 1);
			for (int i = 0; i < numFeatures; ++i) {
				instance.setValue((Attribute)featureNames.elementAt(i), aPoint.vector[i]);
			}
			if (aPoint.genre.equals(genreToIdentify)) {
				instance.setValue((Attribute)featureNames.elementAt(numFeatures), "positive");
				poscount += 1;
			}
			else {
				instance.setValue((Attribute)featureNames.elementAt(numFeatures), "negative");
			}
			trainingSet.add(instance);
			simpleListOfInstances.add(instance);
		}
		
		if (verbose) {
			writer.print(genreToIdentify + " count: " + poscount + "\n");
		}
		System.out.println(featureNames.size());
		System.out.println(genreToIdentify + " count: " + poscount);
		
		try {
			String[] options = {"-R", ridgeParameter};
			logistic = Classifier.forName("weka.classifiers.functions.Logistic", options);
			logistic.buildClassifier(trainingSet);
			if (verbose) {
				writer.print(logistic.toString());
			}
			 
			Evaluation eTest = new Evaluation(trainingSet);
			eTest.evaluateModel(logistic, trainingSet);
			 
			String strSummary = eTest.toSummaryString();
			if (verbose) {
				writer.print(strSummary);
			}
			
			for (int i = 0; i < numInstances; ++i) {
				Instance anInstance = simpleListOfInstances.get(i);
				memberProbs[i] = logistic.distributionForInstance(anInstance);
			}
			// Get the confusion matrix
			double[][] cmMatrix = eTest.confusionMatrix();
			if (verbose) {
				writer.print("      Really " + genreToIdentify + "     other.");
				writer.print("===================================");
				String[] lineheads = {"ID'd " + genreToIdentify+ ":  ", "ID'd other "};
				for (int i = 0; i < 2; ++i) {
					double[] row = cmMatrix[i];
					writer.print(lineheads[i] + Integer.toString((int) row[0]) + "             " + Integer.toString((int) row[1]));
				}
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
				testProbs[i] = logistic.distributionForInstance(anInstance);
			}
		}
		catch (Exception e) {
			System.out.println(e);
		}
		
		return testProbs;
	}
	

}
