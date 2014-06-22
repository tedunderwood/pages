/**
 * 
 */
package collate;

import java.util.concurrent.Callable;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import pages.PairtreeReader;

/**
 * @author tunder
 *
 */
public class VolumeSummarizer implements Callable<Map<String, Integer>> {
	
	String cleanID;
	String pairtreeRoot;
	String genremapDirectory;
	String[] arbitrary = {"the", "of", "and", "a", "to", "an", "is"};
	
	public VolumeSummarizer(String cleanID, String pairtreeRoot, String genremapDirectory) {
		this.cleanID = cleanID;
		this.pairtreeRoot = pairtreeRoot;
		this.genremapDirectory = genremapDirectory;
	}
	
	public Map<String, Integer> call() {
		Map<String, Integer> wordsPerGenre = new HashMap<String, Integer>();
		wordsPerGenre.put("in", 0);
		wordsPerGenre.put("out", 0);
		PairtreeReader pairtree = new PairtreeReader(pairtreeRoot);
		ArrayList<String> filelines = pairtree.getVolume(cleanID);
		
		System.out.println("Lines: " + Integer.toString(filelines.size()));
		
		boolean included = false;
		for (String line : filelines){
			String[] tokens = line.split("\t");
			String word = tokens[1];
			for (String match : arbitrary) {
				if (match.equals(word)) included = true;
			}
			if (included) wordsPerGenre.put("in", wordsPerGenre.get("in") + 1);
			else wordsPerGenre.put("out", wordsPerGenre.get("out") + 1);
		}
		
		return wordsPerGenre;
	}
}
