/**
 * 
 */
package pages;
import java.util.ArrayList;

/**
 * @author tunderwood
 *
 */
public class SubdividedCorpus {
	int folds;
	TrainingCorpus corpus;
	ArrayList<ArrayList<DataPoint>> partitionedPoints;
	ArrayList<ArrayList<String>> partitionedVolumes;
	
	public SubdividedCorpus(TrainingCorpus corpus, int folds) {
		this.corpus = corpus;
		this.folds = folds;
		ArrayList<String> volumeIDs = corpus.trainingVols;
		
		partitionedVolumes = new ArrayList<ArrayList<String>>();
		for (int i = 0; i < folds; ++i) {
			partitionedVolumes.add(new ArrayList<String>());
		}
		for (int i = 0; i < volumeIDs.size(); ++i) {
			int remainder = i % folds;
			partitionedVolumes.get(remainder).add(volumeIDs.get(i));
		}
		
		ArrayList<DataPoint> datapoints = corpus.datapoints;
		partitionedPoints = new ArrayList<ArrayList<DataPoint>>();
		for (int i = 0; i < folds; ++i) {
			partitionedPoints.add(new ArrayList<DataPoint>());
		}
		
		for (int i = 0; i < datapoints.size(); ++i) {
			DataPoint aPoint = datapoints.get(i);
			String volume = aPoint.volume;
			int whichPartition = -1;
			for (int j = 0; j < folds; ++j) {
				if (partitionedVolumes.get(j).contains(volume)) {
					whichPartition = j;
				}
			}
			if (whichPartition < 0 | whichPartition >= folds) {
				System.out.println("Partition error.");
			}
			else {
				partitionedPoints.get(whichPartition).add(aPoint);
			}
		}
	}
	
	public ArrayList<DataPoint> pointsExcluding(int toExclude) {
		if (toExclude >= folds | toExclude < 0) {
			System.out.println("Attempting to exclude a partition that does not exist: " + toExclude);
			return null;
			// TODO: better error handling.
		}
		
		ArrayList<DataPoint> subset = new ArrayList<DataPoint>();
		for (int i = 0; i < folds; ++i) {
			if (i != toExclude) {
				subset.addAll(partitionedPoints.get(i));
			}
		}
		
		return subset;
	}
	
	public ArrayList<DataPoint> pointsOnly(int toTake) {
		if (toTake >= folds | toTake < 0) {
			System.out.println("Attempting to exclude a partition that does not exist: " + toTake);
			return null;
			// TODO: better error handling.
		}
		
		return partitionedPoints.get(toTake);
	}
	
	public ArrayList<String> volumesExcluding(int toExclude) {
		ArrayList<String> everythingBut = new ArrayList<String>();
		
		for (int i = 0; i < folds; ++i) {
			if (i != toExclude) {
				everythingBut.addAll(partitionedVolumes.get(i));
			}
		}
		return everythingBut;
	}
}
