/**
 * 
 */
package pages;

import java.util.ArrayList;

/**
 * Each instance of this class packages and interprets predictions made by multiple
 * classifiers about a single volume. "Interpretation" means selecting the most probable
 * genre for each page in the volume, but also involves generating summary
 * statistics that help us assess confidence about this prediction.
 * 
 * @author tunder
 * 
 * @param predictions An array holding the most probable genre for each page.
 * @param averageMaxProb The average, over all pages, of the probability predicted
 * for the genre that was most probable on each page.
 * @param averageGap The average gap between the most probable genre for each page
 * and the next most probable genre for that page.
 *
 */
public class ClassificationResult {
	
	ArrayList<String> predictions;
	double averageMaxProb;
	double averageGap;
	
	ClassificationResult(ArrayList<double[]> probabilitiesPerPageAndGenre, 
			int numGenres, ArrayList<String> genres) {
		
		int numPages = probabilitiesPerPageAndGenre.size();
		
		predictions = new ArrayList<String>(numPages);
		for (int i = 0; i < numPages; ++i) {
			predictions.add("none");
		}
		
		double sumOfMaxProbs = 0d;
		double sumOfGaps = 0d;
	
		for (int i = 0; i < numPages; ++i) {
			double maxprob = 0d;
			double gapBetweenTopAndNext = 0d;
			for (int j = 0; j < numGenres; ++j) {
				double probabilityPageIsGenreJ = probabilitiesPerPageAndGenre.get(i)[j];
				if (probabilityPageIsGenreJ > maxprob) {
					gapBetweenTopAndNext = probabilityPageIsGenreJ - maxprob;
					maxprob = probabilityPageIsGenreJ;
					predictions.set(i, genres.get(j));
				}
			}
			
			sumOfMaxProbs += maxprob;
			sumOfGaps += gapBetweenTopAndNext;
		}
		
		averageMaxProb = sumOfMaxProbs / numPages;
		averageGap = sumOfGaps / numPages;
	}

}
