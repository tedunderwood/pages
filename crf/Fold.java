/**
 * 
 */
package crf;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.core.Instance;

/**
 * @author tunder
 *
 */
public class Fold implements java.io.Serializable {
		
	Classifier forest;
	Instances trainingSet;
	int numInstances;
	double[][] memberProbs;
	private static final long serialVersionUID = 145L;
	
	public Fold (Instances trainingSet, int foldNumber, boolean verbose) {
		this.trainingSet = trainingSet;
		numInstances = trainingSet.numInstances();
		memberProbs = new double[numInstances][4];
		
		String outpath = "/Volumes/TARDIS/output/forests/fold" + Integer.toString(foldNumber) + ".txt";
		
		LineWriter writer = new LineWriter(outpath, true);
		
		try {
			String[] options = {"-I", "50", "-K", "5"};
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
	
	public double[][] testNewInstances(Instances testSet) {

		int testSize = testSet.numInstances();
		double[][] testProbs = new double[testSize][4];
		
		try {
			for (int i = 0; i < numInstances; ++i) {
				Instance instance = testSet.instance(i);
				testProbs[i] = forest.distributionForInstance(instance);
			}
		}
		catch (Exception e) {
			System.out.println(e);
		}
		
		return testProbs;
	}
	

}

