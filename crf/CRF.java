/**
 * 
 */
package crf;
import weka.core.Instances;
import weka.core.Instance;
import weka.core.Attribute;
import java.io.BufferedReader;
import java.io.FileReader; 

/**
 * @author tunder
 *
 */
public class CRF {
	
	static Instances masterDataset;
	static int numFolds = 5;
	
	public static void main(String[] args) {
		// We begin by reading a dataset (Instances class) from an .arff file.
		try {
			BufferedReader reader = new BufferedReader(
			                              new FileReader("/Users/tunder/Dropbox/pagedata/errors.arff"));
			masterDataset = new Instances(reader);
			reader.close();
		}
		catch (Exception e) {
		}
		
		if (masterDataset == null) {
			// We have had an io error, such as FileNotFound.
			System.exit(0);
		}
		
		
		 // setting class attribute
		masterDataset.setClassIndex(masterDataset.numAttributes() - 1);
		
		// Now we're going to repeatedly divide the dataset into training and
		// test sets for crossvalidation. We do this numFolds times.
		// Unfortunately in doing this we have to keep Instances that belong
		// to the same volume together, so we need to use the ID attribute
		// to divide the dataset rather than simply selecting a random subset
		// of Instances.
		
		// First find that ID attribute.
		Attribute idAttribute = masterDataset.attribute("ID");
		int idIndex = idAttribute.index();
		
		// How many distinct values does it have?
		int numIDVals = masterDataset.numDistinctValues(idIndex);
		int foldSize = numIDVals / numFolds;
		int numInstances = masterDataset.numInstances();
		double[][] allResults = new double[numInstances][4];
		
		// Now we begin a loop that will repeat as many times
		// as we have "folds" in our crossvalidation. E.g., in
		// fivefold crossvalidation, it will repeat five times.
		for (int f = 0; f < numFolds; ++f) {
			// We can predict startID and endID for fold f simply by knowing how many
			// IDs (i.e., volumes) are in each fold. Of course, since the
			// number of IDs may not be precisely divisible by numFolds, we
			// have to allow for the possibility of a remainder in the last
			// fold.
			int startID = f * foldSize;
			int endID;
			if (f < numFolds -1) {
				endID = (f + 1) * foldSize;
			} else {
				endID = numIDVals;
			}
			
			// Now we know the *volumes* that start and end this fold. But to
			// map volumes to page instances we have to actually iterate through
			// the instances.
			int startInstance = -1;
			int endInstance = -1;
			for (int i = 0; i < numInstances; ++i) {
				Instance thisInstance = masterDataset.instance(i);
				int idVal = (int) thisInstance.value(idIndex);
				if (idVal >= startID & startInstance < 0) startInstance = i;
				if (idVal > endID & endInstance < 0) {
					endInstance = i;
					break;
				}
			}
			// If the endID is the last Id in the dataset, the algorithm above
			// will never declare an endInstance. In that case ...
			if (endInstance < 0) {
				endInstance = numInstances;
			}
			
			// Creating the test set is easy, because we can use a constructor that
			// slices a subset of our master dataset.
			int testSetSize = endInstance - startInstance;
			Instances testSet = new Instances(masterDataset, startInstance, testSetSize);
			
			// Creating a training set is less easy. We have to initialize a blank
			// set of Instances.
			int trainingSetSize = numInstances - testSetSize;
			Instances trainingSet = new Instances(masterDataset, trainingSetSize);
			
			// Then we iterate through the master Dataset and add instances one by one.
			for (int i = 0; i < numInstances; ++i) {
				Instance thisInstance = masterDataset.instance(i);
				int idVal = (int) thisInstance.value(idIndex);
				if (idVal < startInstance | idVal >= endInstance) {
					trainingSet.add(thisInstance);
				}
			}
			
			// Create a classifier.
			Fold classifier = new Fold(trainingSet, f, true);
			
			// Test the testset, and copy the results to the appropriate location
			// in the master results array.
			double[][] results = classifier.testNewInstances(testSet);
			
			for (int offset = 0; offset < testSetSize; ++ offset) {
				int absoluteIndex = startInstance + offset;
				allResults[absoluteIndex] = results[offset];
			}
		
		// This ends the loop over "folds" of crossvalidation.
		}
	
		// Now to output results.
		Attribute classAttribute = masterDataset.classAttribute();
		String[] outLines = new String[numInstances];
		for (int i = 0; i < numInstances; ++i) {
			Instance instance = masterDataset.instance(i);
			String classValue = instance.stringValue(classAttribute);
			String outLine = classValue;
			double[] probs = allResults[i];
			for (double prob : probs) {
				outLine = outLine + "\t" + Double.toString(prob);
			}
			outLines[i] = outLine;
		}
		
		LineWriter writer = new LineWriter("/Volumes/TARDIS/output/forests/probabilities.tsv", false);
		writer.send(outLines);
	}
}
