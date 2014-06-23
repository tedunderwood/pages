/**
 * 
 */
package collate;

import java.util.concurrent.Callable;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import pages.PairtreeReader;
import pages.LineReader;
import pages.InputFileException;

/**
 * @author tunder
 *
 */
public class VolumeSummarizer implements Callable<VolumeSummary> {
	
	String cleanID;
	String pairtreeRoot;
	String genremapDirectory;
	String[] allowableGenres = {"ads", "back", "bio", "dra", "fic", "front", "poe", "non"};
	
	public VolumeSummarizer(String cleanID, String pairtreeRoot, String genremapDirectory) {
		this.cleanID = cleanID;
		this.pairtreeRoot = pairtreeRoot;
		this.genremapDirectory = genremapDirectory;
	}
	
	public VolumeSummary call() {
		Map<String, Integer> wordsPerGenre = new HashMap<String, Integer>();
		for (String genre : allowableGenres) {
			wordsPerGenre.put(genre, 0);
		}
		
		HashMap<Integer, Integer> wordsPerPage = new HashMap<Integer, Integer>();
		// We're going to collect word counts initially as a map, because we don't know
		// how many pages there will be, and cannot guarantee that they will be sequential.
		
		PairtreeReader pairtree = new PairtreeReader(pairtreeRoot);
		ArrayList<String> filelines = pairtree.getVolume(cleanID);
		
		System.out.println("Lines: " + Integer.toString(filelines.size()));
		
		int maxpage = 0;
		
		for (String line : filelines){
			String[] tokens = line.split("\t");
			
			if (tokens.length != 3) {
				System.out.println("More or less than three columns in \t" + cleanID);
			}
			
			int page = -1;
			String word = tokens[1];
			if (word.startsWith("#")) continue;
			
			int count = 0;
			try {
				page = Integer.parseInt(tokens[0]);
				count = Integer.parseInt(tokens[2]);
			} catch (Exception e) {
				System.out.println("Failure to parse as integer in \t" + cleanID);
				continue;
			}
			
			if (page > maxpage) maxpage = page;
			
			if (wordsPerPage.containsKey(page)) {
				wordsPerPage.put(page, wordsPerPage.get(page) + count);
			} else {
				wordsPerPage.put(page, count);
			}
			
		}
		
		// Now we guarantee that wordsPerPage has a value for everything
		// between 0 and maxpage.
		for (int i = 0; i <= maxpage; ++ i) {
			// Note less than *or equal to* because maxpage = length - 1 .
			if (!wordsPerPage.containsKey(i)) wordsPerPage.put(i, 0);
		}
		
		if (!genremapDirectory.endsWith("/")) genremapDirectory = genremapDirectory + "/";
		String genrePath = genremapDirectory + cleanID + ".predict";
		LineReader reader = new LineReader(genrePath);
		
		boolean failedRead = false;
		try {
			filelines = reader.readList();
		} catch (InputFileException e) {
			System.out.println("Failed to read file \t" + genrePath);
			failedRead = true;
		}
		
		boolean paginationProblem = false;
		if (filelines.size() != (maxpage + 1)) {
			System.out.println("Pagination problem with \t" + cleanID + "\t" + 
					Integer.toString(filelines.size()) + "\t" + Integer.toString(maxpage + 1));
			paginationProblem = true;
		}
		
		if (failedRead | paginationProblem) {
			return null;
		} else {
			for (int i = 0; i < filelines.size(); ++ i) {
				String line = filelines.get(i);
				int wordcount = wordsPerPage.get(i);
				String[] tokens = line.split("\t");
				String genre = tokens[2];
				// We're taking the smoothed genre.
				if (wordsPerGenre.containsKey(genre)) {
					wordsPerGenre.put(genre, wordsPerGenre.get(genre) + wordcount);
				}
			}
		}
		
		int totalWordCount = 0;
		for (String genre : wordsPerGenre.keySet()) {
			totalWordCount += wordsPerGenre.get(genre);
		}
		int majority = totalWordCount / 2;
		
		String volumeGenre = "mixed";
		if (wordsPerGenre.get("fic") > majority) volumeGenre = "fiction";
		if (wordsPerGenre.get("dra") > majority) volumeGenre = "drama";
		if (wordsPerGenre.get("poe") > majority) volumeGenre = "poetry";
		if (wordsPerGenre.get("bio") > majority) volumeGenre = "nonfiction";
		if (wordsPerGenre.get("non") > majority) volumeGenre = "nonfiction";
		
		VolumeSummary thisVolume = new VolumeSummary(cleanID, volumeGenre, wordsPerGenre);
		
		return thisVolume;
	}
}
