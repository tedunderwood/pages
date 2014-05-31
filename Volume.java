package pages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;

/**
 * @author tunderwood
 * 
 * This class acts as a reducer, collecting the features associated with a single docID
 * (aka volumeID).
 * 
 * addFeature is a method that gets called for every feature the Volume receives.
 * A feature is defined as an array of four strings: pageNum, formField, word, and count.
 * 
 * Then, after all features are received, there are two different methods that could be used 
 * to transform Volumes into DataPoints in vector space.
 * 
 * makeVolumePoint turns the whole volume into a single DataPoint.
 * 
 * makePagePoint turns the volume into a collection of DataPoints representing pages; these
 * DataPoints get three extra features that reflect structural information about a page's
 * length, shape, and position in the volume.
 * 
 * @param pagesPerVol	HashMap storing the number of pages (value) for a volume ID (key).
 * @param meanLinesPerPage	HashMap storing the mean number of lines per page 
 * 							in a volume ID (key).
 */

public class Volume {
	String volumeID;
	ArrayList<Integer> listOfLineCounts;
	ArrayList<Integer> listOfPages;
	int numberOfPages;
	int maxPageNum;
	int totalWords;
	ArrayList<String[]> sparseTable;
	
	public Volume(String volumeID) {
		this.volumeID = volumeID;
		listOfLineCounts = new ArrayList<Integer>();
		listOfPages = new ArrayList<Integer>();
		numberOfPages = 0;
		maxPageNum = 0;
		totalWords = 0;
		sparseTable = new ArrayList<String[]>();
	}
	/** 
	 * This method accepts a line from the database, already parsed into a
	 * sequence of fields, with docid removed (it is volumeID of this volume).
	 *  
	 * @param feature:	0 - pageNum, 1 - feature, 2 - count
	 * 
	 */
	public void addFeature(String[] feature) {
		
		sparseTable.add(feature);
		
		// The number of pages in the volume is defined as the number of distinct
		// page numbers it receives. Note that this is not necessarily == to the
		// maximum pageNum value. It's possible for some pages to be blank, in
		// which case we might conceivable have no information about them here even though they've
		// increased the max pageNum value.
		
		// In reality, given my current page-level tokenizing script (NormalizeOCR 1.0),
		// it's pretty much *not* possible to have a page without features, because e.g.
		// #textlines gets reported even if zero.
		
		int pageNum = Integer.parseInt(feature[0]);
		if (!listOfPages.contains(pageNum)) {
			listOfPages.add(pageNum);
			numberOfPages = listOfPages.size();
		}
		
		String featurename = feature[1];
		int count = Integer.parseInt(feature[2]);
		
		if (!featurename.startsWith("#")) {
			totalWords += count; 
		}
		
		if (pageNum > maxPageNum) maxPageNum = pageNum;
		
		// If the "word" is actually a structural feature recording the number
		// of lines in a page, this needs to be added to the listOfLineCounts
		// for pages in the volume.
		
		if (featurename .equals("#textlines")) {
			listOfLineCounts.add(count);
		}
		
		// We don't assume that this list will have the same length as the
		// listOfPages, or that there is any mapping between the two. We use
		// it purely to produce a meanLinesPerPage value later.
			
	}
	
	public DataPoint makeVolumePoint(HashMap<String, Integer> vocabularyMap) {
		
		// Create a vector of the requisite dimensionality; initialize to zero.
		int dimensionality = vocabularyMap.size();
		double[] vector = new double[dimensionality];
		Arrays.fill(vector, 0);
		
		// Then sum all occurrences of words to the appropriate vector index.
		for (String[] feature : sparseTable) {
			String word = feature[2];
			if (vocabularyMap.containsKey(word)) {
				int idx = vocabularyMap.get(word);
				double count = Double.parseDouble(feature[3]);
				vector[idx] += count;
			}
		}
		
		DataPoint point = new DataPoint(volumeID, vector);
		return point;
	}
	
	public ArrayList<DataPoint> makePagePoints(HashMap<String, Integer> vocabularyMap) {
		// Page points are much more complex.
		// To start with, divide the sparseTable into page groups.
		
		ArrayList<ArrayList<String[]>> featuresByPage = new ArrayList<ArrayList<String[]>>();
		for (int i = 0; i < numberOfPages; ++ i) {
			ArrayList<String[]> blankPage = new ArrayList<String[]>();
			featuresByPage.add(blankPage);
		}
		
		for (String[] feature : sparseTable) {
			int pageNum = Integer.parseInt(feature[0]);
			if (listOfPages.contains(pageNum)) {
				int idx = listOfPages.indexOf(pageNum);
				ArrayList<String[]> thisPage = featuresByPage.get(idx);
				thisPage.add(feature);
			}
		}
		
		// We create the following eleven "structural" features that are designed to
		// characterize types of paratext by capturing typographical characteristics
		// of pages.
		//
		// "posInVol" = pagenum / totalpages
		// "lineLengthRatio" = textlines / mean lines per page
		// "capRatio" = caplines / textlines
		// "wordRatio" = words on page / mean words per page
		// "distanceFromMid" = abs( 0.5 - posInVol)
		// "allCapRatio" = words in all caps / words on this page
		// "maxInitalRatio" = largest number of repeated initials / textlines
		// "maxPairRatio" = largest number of repeats for alphabetically adjacent initials / textlines
		// "wordsPerLine" = total words on page / total lines on page
		// "totalWords" = total words on page
		// "typeToken" = number of types / total words on page.
		//
		// NOTE that this must match the list of structural features in Global.
		
		int totalTextLines = 0;
		for (int lineCount: listOfLineCounts) {
			totalTextLines += lineCount;
		}
		
		double meanLinesPerPage;
		if (numberOfPages > 0 & totalTextLines > 0) {
			meanLinesPerPage = totalTextLines / (double) numberOfPages;		
		}
		else {
			// avoid division by zero, here and later
			meanLinesPerPage = 1;
			System.out.println("Suspicious mean lines per page in volume " + volumeID);
		}
		
		double meanWordsPerPage;
		if (numberOfPages > 0 & totalWords > 0) {
			meanWordsPerPage = totalWords / (double) numberOfPages;
		}
		else {
			meanWordsPerPage = 1;
			System.out.println("Suspicious mean words per page in volume " + volumeID);
		}
		
		// We're going to create a DataPoint for each page.
		ArrayList<DataPoint> points = new ArrayList<DataPoint>(numberOfPages);
		
		for (int i = 0; i < numberOfPages; ++i) {
			
			ArrayList<String[]> thisPage = featuresByPage.get(i);
			int thisPageNum = listOfPages.get(i);
			
			// Create a vector of the requisite dimensionality; initialize to zero.
			// Note that the dimensionality for page points is 
			// vocabularySize + 11  !! Because structural features.
			
			int vocabularySize = vocabularyMap.size();
			int dimensionality = vocabularySize + 11;
			double[] vector = new double[dimensionality];
			double sumAllWords = 0.0001d;
			// This is a super-cheesy way to avoid div by zero.
			double types = 0;
			Arrays.fill(vector, 0);
			
			// Then sum all occurrences of words to the appropriate vector index.
			double textlines = 0.0001d;
			double caplines = 0;
			double maxinitial = 0;
			double maxpair = 0;
			double allcapwords = 0;
			
			for (String[] feature : thisPage) {
				String word = feature[1];
				if (vocabularyMap.containsKey(word)) {
					types += 1;
					// Note that since "wordNotInVocab" is, paradoxically, in the vocab,
					// this will count separate occurrences of "wordNotInVocab" as new types,
					// and sum their counts.
					int idx = vocabularyMap.get(word);
					double count = Double.parseDouble(feature[2]);
					vector[idx] += count;
					sumAllWords += count;
				}
				
				if (word.equals("#textlines")) {
					textlines = Double.parseDouble(feature[2]);
					continue;
					// Really an integer but cast as double to avoid 
					// integer division. Same below.
				}
				
				if (word.equals("#caplines")) {
					caplines = Double.parseDouble(feature[2]);
					continue;
				}
				
				if (word.equals("#maxinitial")) {
					maxinitial = Double.parseDouble(feature[2]);
					continue;
				}
				
				if (word.equals("#maxpair")) {
					maxpair = Double.parseDouble(feature[2]);
					continue;
				}
				
				if (word.equals("#allcapswords")) {
					allcapwords = Double.parseDouble(feature[2]);
				}
				
			}
			
			// Normalize the feature counts for total words on page:
			
			for (int j = 0; j < vocabularySize; ++ j) {
				vector[j] = vector[j] / sumAllWords;
			}
			
			// Now we have a feature vector with all the words filled in, but
			// the eleven extra spaces at the end are still zero.
			// We need to create structural "page features."
			
			double positionInVol = (double) thisPageNum / maxPageNum;
			// TODO: Error handling to avoid division by zero here.
			
			if (textlines == 0) textlines = 0.1;
			// hack to avoid division by zero
					
			double lengthRatio = textlines / meanLinesPerPage;
			double capRatio = caplines / textlines;
			
			vector[vocabularySize] = positionInVol;
			// normalized by maxPageNum
			vector[vocabularySize + 1] = lengthRatio;
			// length in lines relative to mean for volume
			vector[vocabularySize + 2] = capRatio;
			// proportion of lines that are initial-capitalized
			vector[vocabularySize + 3] = sumAllWords / meanWordsPerPage;
			// wordRatio: length in words relative to mean for volume
			vector[vocabularySize + 4] = Math.abs(thisPageNum - (maxPageNum/2)) / (double) maxPageNum;
			// distanceFrom Mid: absolute distance from midpoint of volume, normalized for length of volume
			vector[vocabularySize + 5] = allcapwords / sumAllWords;
			// "allCapRatio" = words in all caps / words on this page
			vector[vocabularySize + 6] = maxinitial / textlines;
			// "maxInitalRatio" = largest number of repeated initials / textlines
			vector[vocabularySize + 7] = maxpair / textlines;		
			// "maxPairRatio" = largest number of repeats for alphabetically adjacent initials / textlines
			vector[vocabularySize + 8] = sumAllWords / textlines;
			// "wordsPerLine" = total words on page / total lines on page
			vector[vocabularySize + 9] = sumAllWords;
			// "totalWords" = total words on page
			vector[vocabularySize + 10] = types / sumAllWords;
			// type-token ratio
			
			String label = volumeID + "," + Integer.toString(thisPageNum);
			DataPoint thisPoint = new DataPoint(label, vector);
			points.add(thisPoint);
		}
	return points;	
	}

}
