/**
 * 
 */
package hmm;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.Instances;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;

/**
 * @author tunderwood
 *
 */
public class TestWeka {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		 // Read all the instances in the file (ARFF, CSV, XRFF, ...)
		 String filename = "/Users/tunderwood/weka/weka-3-6-8/data/iris.arff";
		 Instances instances;
		 
		 try {
			 DataSource source = new DataSource(filename);
			 instances = source.getDataSet();
		 
			 // Make the last attribute be the class
			 instances.setClassIndex(instances.numAttributes() - 1);
			 
			 // Print header and instances.
			 System.out.println("\nDataset:\n");
			 System.out.println(instances);
			 
			 String[] options = {"-R", ".01"};
			 Classifier logistic = Classifier.forName("weka.classifiers.functions.Logistic", options);
			 // Classifier logistic = (Classifier)new Logistic();
			 
			 logistic.buildClassifier(instances);
			 System.out.println(logistic.toString());
			 
			 Evaluation eTest = new Evaluation(instances);
			 eTest.evaluateModel(logistic, instances);
			 
			 String strSummary = eTest.toSummaryString();
			 System.out.println(strSummary);
			 
			 // Get the confusion matrix
			 // double[][] cmMatrix = eTest.confusionMatrix();
		 }
		 catch (Exception e) {
			 System.out.println(e);
		 } 

	}

}
